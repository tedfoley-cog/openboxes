import React, { useEffect, useState } from 'react';

import { useHistory } from 'react-router-dom';

import { ERROR_DETAILS_API } from 'api/urls';
import Button from 'components/form-elements/Button';
import useTranslation from 'hooks/useTranslation';
import apiClient from 'utils/apiClient';
import Translate from 'utils/Translate';
import HeaderWrapper from 'wrappers/HeaderWrapper';
import PageWrapper from 'wrappers/PageWrapper';

const ErrorDetailsPage = () => {
  useTranslation('errors', 'default');

  const history = useHistory();
  const [details, setDetails] = useState(null);

  useEffect(() => {
    apiClient.get(ERROR_DETAILS_API)
      .then((response) => setDetails(response?.data?.data))
      .catch(() => setDetails(null));
  }, []);

  return (
    <PageWrapper>
      <HeaderWrapper className="align-items-center h-auto py-3">
        <span className="title">
          <Translate id="react.errors.errorDetails.label" defaultMessage="Error Details" />
        </span>
      </HeaderWrapper>
      <div className="p-3">
        <div className="mb-3">
          <Button
            defaultLabel="Ignore error and go back"
            label="react.errors.ignoreError.label"
            variant="primary-outline"
            onClick={() => history.goBack()}
          />
        </div>
        <div className="alert alert-secondary" role="status" data-testid="error-details">
          <strong>
            <Translate id="react.errors.error.label" defaultMessage="Error" />
            {' '}
            {details?.errorCode ?? 500}
            :
          </strong>
          {' '}
          {details?.errorMessage}
          <br />
          <strong>
            <Translate id="react.errors.servlet.label" defaultMessage="Servlet" />
            :
          </strong>
          {' '}
          {details?.servletName}
          <br />
          <strong>
            <Translate id="react.errors.uri.label" defaultMessage="URI" />
            :
          </strong>
          {' '}
          {details?.uri}
          <br />
          {details?.exceptionMessage && (
            <>
              <strong>
                <Translate id="react.errors.exceptionMessage.label" defaultMessage="Exception Message" />
                :
              </strong>
              {` ${details.exceptionMessage}`}
              <br />
            </>
          )}
          {details?.causedBy && (
            <>
              <strong>
                <Translate id="react.errors.causedBy.label" defaultMessage="Caused by" />
                :
              </strong>
              {` ${details.causedBy}`}
              <br />
            </>
          )}
          {details?.className && (
            <>
              <strong>
                <Translate id="react.errors.class.label" defaultMessage="Class" />
                :
              </strong>
              {` ${details.className}`}
              <br />
            </>
          )}
        </div>
        {details?.stackTrace?.length > 0 && (
          <>
            <h2>
              <Translate id="react.errors.stackTrace.label" defaultMessage="Stack Trace" />
            </h2>
            <div className="border p-2" style={{ maxHeight: '300px', overflow: 'auto' }} data-testid="error-stack-trace">
              <pre className="m-0">
                {details.stackTrace.join('\n')}
              </pre>
            </div>
          </>
        )}
      </div>
    </PageWrapper>
  );
};

export default ErrorDetailsPage;
