/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';

import {Form, Labeled, Button, MessageBox} from 'components';
import {t} from 'translation';

import {Header, Footer} from '..';
import {validateLicense, storeLicense} from './service';

import './License.scss';

export default function License() {
  const [licenseInfo, setLicenseInfo] = useState(null);
  const [licenseText, setLicenseText] = useState('');
  const [willReload, setWillReload] = useState(false);

  useEffect(() => {
    (async () => setLicenseInfo(await validateLicense()))();
  }, []);

  return (
    <>
      <Header noActions />
      <main className="License">
        {licenseInfo && (
          <MessageBox type={licenseInfo.errorCode ? 'error' : 'success'}>
            {licenseInfo.errorCode ? (
              t('apiErrors.' + licenseInfo.errorCode)
            ) : (
              <>
                {t('license.licensedFor')} {licenseInfo.customerId}.{' '}
                {!licenseInfo.unlimited && (
                  <>
                    {t('license.validUntil')} {new Date(licenseInfo.validUntil).toUTCString()}.{' '}
                  </>
                )}
                {willReload && (
                  <span dangerouslySetInnerHTML={{__html: t('license.redirectMessage')}} />
                )}
              </>
            )}
          </MessageBox>
        )}
        <Form
          compact
          onSubmit={async evt => {
            evt.preventDefault();

            const result = await storeLicense(licenseText);

            if (!result.errorCode) {
              setTimeout(() => (window.location.href = './'), 10000);
              setWillReload(true);
            }

            setLicenseInfo(result);
          }}
        >
          <Labeled label={t('license.licenseKey')}>
            <textarea
              rows="12"
              placeholder={t('license.enterLicense')}
              value={licenseText}
              onChange={evt => setLicenseText(evt.target.value)}
            ></textarea>
          </Labeled>
          <Button type="submit">{t('license.submit')}</Button>
        </Form>
      </main>
      <Footer />
    </>
  );
}
