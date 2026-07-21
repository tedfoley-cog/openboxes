#!/usr/bin/env python3
"""
API characterization (snapshot) test runner for OpenBoxes.

Authenticates against a running, demo-data-seeded OpenBoxes instance
(see docs/migration/RUNNING_LOCALLY.md), hits every endpoint declared in
endpoints_a.py, normalizes the responses and compares them against the
committed snapshots in snapshots/.

Usage:
    python3 snapshot_runner.py            # verify against committed snapshots
    python3 snapshot_runner.py --update   # re-baseline (rewrite snapshots)

Environment overrides:
    OPENBOXES_URL       (default http://localhost:8080/openboxes)
    OPENBOXES_USERNAME  (default admin)
    OPENBOXES_PASSWORD  (default password)

Normalization / masking rules (documented for reviewers):
  1. Entity ids: OpenBoxes uses 24+ char lowercase-hex UUIDs generated at
     demo-data load time, so any string value matching ^[0-9a-f]{16,}$ is
     masked to "<ID>". Values of keys named "id" or ending in "Id"/".id"
     that are non-numeric strings are masked too.
  2. Dates/timestamps: string values matching common ISO-8601 /
     "yyyy-MM-dd", "MM/dd/yyyy", "dd/MMM/yyyy", RFC-1123, "Month yyyy"
     (abbreviated or full month names), "FY nn" fiscal-year labels and
     epoch-millis patterns are masked to "<DATE>". Keys that look like dates (used as
     map keys in dashboard time-series) are masked to "<DATEKEY>" (with a
     stable numeric suffix to keep entries distinct).
  2b. Random sequence identifiers (identifier template "NNNLLL", e.g.
     organization codes like "MO-099VCA") are generated at demo-data load
     time; inline occurrences of \\d{3}[A-Z]{3} are masked to "<SEQ>".
  3. Volatile-by-name keys (masked regardless of value): dateCreated,
     lastUpdated, buildNumber, buildDate, branchName, ipAddress, hostname,
     timezone, minimumExpirationDate, sessionId, requestId, timestamp.
  4. Object keys are emitted sorted; arrays are sorted canonically by
     their masked JSON serialization (DB result ordering is not
     deterministic across loads).
  5. Non-JSON bodies (CSV templates, plain text) are stored as raw text
     with the date patterns from rule 2 masked.
  6. Recorded per endpoint: request method+path, HTTP status, normalized
     content type, normalized body.
"""

import argparse
import difflib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

HERE = os.path.dirname(os.path.abspath(__file__))
SNAPSHOT_DIR = os.path.join(HERE, "snapshots")

BASE_URL = os.environ.get("OPENBOXES_URL", "http://localhost:8080/openboxes").rstrip("/")
USERNAME = os.environ.get("OPENBOXES_USERNAME", "admin")
PASSWORD = os.environ.get("OPENBOXES_PASSWORD", "password")

HEX_ID_RE = re.compile(r"^[0-9a-f]{16,}$")
ID_KEY_RE = re.compile(r"(^id$|Id$|\.id$)")
DATE_VALUE_RES = [
    re.compile(r"^\d{4}-\d{2}-\d{2}([T ].*)?$"),                    # ISO date/datetime
    re.compile(r"^\d{2}/\d{2}/\d{4}([ T].*)?$"),                    # MM/dd/yyyy
    re.compile(r"^\d{2}/[A-Za-z]{3}/\d{4}.*$"),                     # dd/MMM/yyyy
    re.compile(r"^[A-Za-z]{3}, \d{1,2} [A-Za-z]{3} \d{4}.*$"),      # RFC-1123
    re.compile(r"^[A-Za-z]{3,9} \d{4}$"),                           # "Jul 2026" / "April 2026" series keys
    re.compile(r"^FY ?\d{2,4}$"),                                   # "FY 26" fiscal-year series keys
    re.compile(r"^\d{13}$"),                                        # epoch millis
]
INLINE_DATE_RE = re.compile(
    r"(\d{4}-\d{2}-\d{2}([T ][0-9:.+Z-]+)?|\d{2}/\d{2}/\d{4}|[A-Za-z]{3} \d{1,2}, \d{4})"
)
# Random identifiers (openboxes.identifier.default.random.template = "NNNLLL")
# are generated at demo-data load time: organization codes like "MO-099VCA",
# shipment/order numbers, and displayNames embedding them.
INLINE_SEQ_RE = re.compile(r"\b\d{3}[A-Z]{3}\b")
VOLATILE_KEYS = {
    "dateCreated", "lastUpdated", "buildNumber", "buildDate", "branchName",
    "ipAddress", "hostname", "timezone", "minimumExpirationDate",
    "sessionId", "requestId", "timestamp", "expirationDate", "lastUpdated",
    "dateApproved", "dateRequested", "dateShipped", "dateReceived",
}


def looks_like_date(value):
    return isinstance(value, str) and any(r.match(value) for r in DATE_VALUE_RES)


def mask(value, key=None):
    if isinstance(value, dict):
        out = {}
        for k in sorted(value.keys()):
            mk = k
            if looks_like_date(k):
                # keep distinct entries distinct and ordering stable
                mk = "<DATEKEY:%d>" % sorted(
                    kk for kk in value.keys() if looks_like_date(kk)
                ).index(k)
            out[mk] = mask(value[k], key=k)
        return out
    if isinstance(value, list):
        masked = [mask(v, key=key) for v in value]
        return sorted(masked, key=lambda v: json.dumps(v, sort_keys=True))
    if isinstance(value, str):
        if key in VOLATILE_KEYS:
            return "<VOLATILE>"
        if HEX_ID_RE.match(value):
            return "<ID>"
        if key is not None and ID_KEY_RE.search(str(key)) and not value.isdigit():
            return "<ID>"
        if looks_like_date(value):
            return "<DATE>"
        return INLINE_SEQ_RE.sub("<SEQ>", INLINE_DATE_RE.sub("<DATE>", value))
    if key in VOLATILE_KEYS:
        return "<VOLATILE>"
    return value


def normalize_body(content_type, body_bytes):
    text = body_bytes.decode("utf-8", errors="replace")
    if "json" in (content_type or ""):
        try:
            return mask(json.loads(text))
        except ValueError:
            pass
    # non-JSON (CSV / text / html): mask inline dates only
    return {"text": INLINE_DATE_RE.sub("<DATE>", text)}


class Client:
    def __init__(self):
        self.cookies = CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cookies)
        )

    def request(self, method, path, json_body=None, headers=None):
        url = BASE_URL + path
        data = None
        hdrs = {"Accept": "application/json"}
        if json_body is not None:
            data = json.dumps(json_body).encode()
            hdrs["Content-Type"] = "application/json"
        if headers:
            hdrs.update(headers)
        req = urllib.request.Request(url, data=data, method=method, headers=hdrs)
        try:
            resp = self.opener.open(req, timeout=120)
            return resp.status, resp.headers.get("Content-Type", ""), resp.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers.get("Content-Type", ""), e.read()

    def get_json(self, path):
        status, ctype, body = self.request("GET", path)
        text = body.decode("utf-8", errors="replace")
        try:
            return json.loads(text)
        except ValueError:
            raise SystemExit(
                "Expected JSON from GET %s but got HTTP %s (%s): %.500s"
                % (path, status, ctype, text))


def snapshot_record(name, method, path, status, content_type, body):
    # strip charset etc. from content type
    ctype = (content_type or "").split(";")[0].strip()
    return {
        "name": name,
        "request": {"method": method, "path": path},
        "status": status,
        "contentType": ctype,
        "body": normalize_body(ctype, body),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--update", action="store_true", help="re-baseline snapshots")
    parser.add_argument("--only", help="only run endpoints whose name contains this substring")
    args = parser.parse_args()

    from endpoints_a import build_plan  # noqa: E402 (sibling module)

    client = Client()
    plan, context = build_plan(client, BASE_URL, USERNAME, PASSWORD)

    os.makedirs(SNAPSHOT_DIR, exist_ok=True)
    failures = []
    ran = 0
    for entry in plan:
        name = entry["name"]
        if args.only and args.only not in name:
            continue
        ran += 1
        path = entry["path"]() if callable(entry["path"]) else entry["path"]
        if path is None or "/None" in path:
            failures.append(
                "%s: could not resolve path — this endpoint depends on an id "
                "captured by an earlier endpoint (run without --only, or with a "
                "broader filter that includes the whole flow)" % name
            )
            continue
        json_body = entry.get("json")
        if callable(json_body):
            json_body = json_body()
        method = entry.get("method", "GET")
        status, ctype, body = client.request(method, path, json_body=json_body)
        if entry.get("capture"):
            entry["capture"](status, body)
        # mask resolved runtime ids in the recorded request path
        display_path = context.mask_path(path)
        record = snapshot_record(name, method, display_path, status, ctype, body)
        expected_status = entry.get("expect_status")
        if expected_status is not None and status != expected_status:
            failures.append(
                "%s: expected HTTP %s, got %s\n%s"
                % (name, expected_status, status, body[:2000])
            )
            continue
        snap_path = os.path.join(SNAPSHOT_DIR, name + ".json")
        rendered = json.dumps(record, indent=2, sort_keys=True) + "\n"
        if args.update:
            with open(snap_path, "w") as f:
                f.write(rendered)
            print("baselined  %s" % name)
        else:
            if not os.path.exists(snap_path):
                failures.append("%s: missing snapshot %s (run with --update)" % (name, snap_path))
                continue
            with open(snap_path) as f:
                expected = f.read()
            if expected != rendered:
                diff = "\n".join(
                    difflib.unified_diff(
                        expected.splitlines(),
                        rendered.splitlines(),
                        fromfile="snapshots/%s.json (committed)" % name,
                        tofile="live response (normalized)",
                        lineterm="",
                    )
                )
                failures.append("%s: snapshot mismatch\n%s" % (name, diff))
            else:
                print("ok         %s" % name)

    print()
    if failures:
        print("FAILED (%d/%d endpoints):" % (len(failures), ran))
        for f in failures:
            print("-" * 72)
            print(f)
        sys.exit(1)
    print("PASSED: %d endpoints %s" % (ran, "re-baselined" if args.update else "verified"))


if __name__ == "__main__":
    main()
