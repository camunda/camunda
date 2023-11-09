/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';
import {Form, TextArea, Button, InlineNotification, Grid, Column, Stack, Link} from '@carbon/react';

import {PageTitle} from 'components';
import {t} from 'translation';
import {addHandler, removeHandler} from 'request';
import {resetOutstandingRequests, createOutstandingRequestPromise} from 'services';
import {useErrorHandling} from 'hooks';

import {Header, Footer} from '..';
import {validateLicense, storeLicense} from './service';

import './License.scss';

export default function License() {
  const [licenseInfo, setLicenseInfo] = useState(null);
  const [licenseText, setLicenseText] = useState('');
  const [willReload, setWillReload] = useState(false);
  const {mightFail, error, resetError} = useErrorHandling();

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
        <Grid>
          <Column
            sm={{
              start: 1,
              end: 8,
            }}
            md={{
              start: 2,
              end: 8,
            }}
            lg={{
              start: 5,
              end: 13,
            }}
          >
            <Form
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
              <Stack gap={8}>
                {licenseInfo && !error && (
                  <InlineNotification
                    kind="success"
                    hideCloseButton
                    subtitle={formatLicenseInfo(licenseInfo)}
                  />
                )}
                {willReload && <Link href="./">{t('license.clickToLogin')}</Link>}
                {error && (
                  <InlineNotification
                    kind="error"
                    aria-label={t('common.closeError')}
                    statusIconDescription={t('common.error')}
                    onCloseButtonClick={() => {
                      resetError();
                    }}
                    subtitle={error.message}
                  />
                )}
                <TextArea
                  id="LicenseTextArea"
                  rows={12}
                  labelText={t('license.licenseKey')}
                  placeholder={t('license.enterLicense')}
                  value={licenseText}
                  onChange={(evt) => setLicenseText(evt.target.value)}
                />
                <Button type="submit">{t('license.submit')}</Button>
              </Stack>
            </Form>
          </Column>
        </Grid>
      </main>
      <Footer />
    </>
  );
}

function formatLicenseInfo({customerId, unlimited, validUntil}) {
  let formattedInfo = `${t('license.licensedFor')} ${customerId}.`;
  if (!unlimited) {
    formattedInfo += ` ${t('license.validUntil')} ${t('license.validUntil')} ${new Date(
      validUntil
    ).toUTCString()}`;
  }

  return formattedInfo;
}
