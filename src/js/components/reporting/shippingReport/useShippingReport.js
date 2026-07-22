import { useEffect, useState } from 'react';

import { useLocation } from 'react-router-dom';

import reportApi from 'api/services/ReportApi';

/**
 * Loads the shipment checklist data backing the print report screens.
 * The legacy GSP actions took the shipment via a "shipment.id" query param;
 * the React routes keep the same URL contract.
 */
const useShippingReport = () => {
  const { search } = useLocation();

  useEffect(() => {
    document.body.classList.add('print-report');
    return () => document.body.classList.remove('print-report');
  }, []);

  const shipmentId = new URLSearchParams(search).get('shipment.id');
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!shipmentId) {
      setError('missing');
      return;
    }
    reportApi.getShippingReport(shipmentId)
      .then((response) => setData(response.data?.data))
      .catch((err) => setError(err?.response?.data?.errorMessage || 'error'));
  }, [shipmentId]);

  return { data, error, shipmentId };
};

export const groupEntriesByContainer = (entries) => {
  const groups = [];
  (entries ?? []).forEach((entry) => {
    const key = entry.container?.id ?? null;
    const lastGroup = groups[groups.length - 1];
    if (lastGroup && lastGroup.key === key) {
      lastGroup.entries.push(entry);
    } else {
      groups.push({ key, container: entry.container, entries: [entry] });
    }
  });
  return groups;
};

export const containerLabel = (container, translate) => {
  if (!container?.name) {
    return translate('react.shippingReport.unpacked.label', 'Unpacked');
  }
  return container.parentContainerName
    ? `${container.parentContainerName} \u203A ${container.name}`
    : container.name;
};

export default useShippingReport;
