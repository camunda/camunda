/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';

import {Form, Labeled, Button, MessageBox, PageTitle} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {addHandler, removeHandler} from 'request';
import {resetOutstandingRequests, createOutstandingRequestPromise} from 'services';

import {Header, Footer} from '..';
import {validateLicense, storeLicense} from './service';

import './License.scss';

export function License({mightFail, error, resetError}) {
  const [licenseInfo, setLicenseInfo] = useState(null);
  const [licenseText, setLicenseText] = useState('');
  const [willReload, setWillReload] = useState(false);

  useEffect(() => {
    mightFail(validateLicense(), setLicenseInfo);
  }, [mightFail]);

  useEffect(() => {
    const handleResponse = async (response, payload) => {
      const {status} = response;
      if (status >= 400) {
        const {url} = payload;
        const {errorCode} = await response.clone().json();

        // all the other requests are returning this errorCode so we just mute them out
        if (errorCode === 'noLicenseStoredError' && url !== 'api/license/validate') {
          return createOutstandingRequestPromise(payload);
        }
      }

      // we allow the errors from api/license/validate to pass to display error message
      return response;
    };
    addHandler(handleResponse);

    return () => {
      removeHandler(handleResponse);
      resetOutstandingRequests();
    };
  }, []);

  return (
    <>
      <PageTitle pageName={t('license.label')} />
      <Header noActions />
      <main className="License">
        {(licenseInfo || error) && (
          <MessageBox type={error ? 'error' : 'success'}>
            {error ? (
              error.message
            ) : (
              <>
                {t('license.licensedFor')} {licenseInfo.customerId}.{' '}
                {!licenseInfo.unlimited && (
                  <>
                    {t('license.validUntil')} {new Date(licenseInfo.validUntil).toUTCString()}.{' '}
                  </>
                )}
                {willReload && <span>{t('license.redirectMessage')}</span>}
              </>
            )}
          </MessageBox>
        )}
        <Form
          compact
          onSubmit={(evt) => {
            evt.preventDefault();

            mightFail(storeLicense(licenseText), (license) => {
              resetError();
              setLicenseInfo(license);
              setTimeout(() => (window.location.href = './'), 10000);
              setWillReload(true);
            });
          }}
        >
          <Labeled label={t('license.licenseKey')}>
            <textarea
              rows="12"
              placeholder={t('license.enterLicense')}
              value={licenseText}
              onChange={(evt) => setLicenseText(evt.target.value)}
            ></textarea>
          </Labeled>
          <Button type="submit">{t('license.submit')}</Button>
        </Form>
      </main>
      <Footer />
    </>
  );
}

export default withErrorHandling(License);
