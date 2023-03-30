/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';

import {CarbonModal as Modal, LabeledInput, DocsLink} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError, addNotification} from 'notifications';
import {isMetadataTelemetryEnabled, loadConfig} from 'config';

import {updateTelemetry} from './service';

import './TelemetrySettings.scss';

export function TelemetrySettings({onClose, mightFail}) {
  const [telemetryEnabled, setTelemetryEnabled] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  // set initial state of the checkbox
  useEffect(() => {
    (async () => {
      setTelemetryEnabled(await isMetadataTelemetryEnabled());
    })();
  }, []);

  function submit() {
    setIsLoading(true);
    mightFail(
      updateTelemetry(telemetryEnabled),
      () => {
        addNotification({type: 'success', text: t('telemetry.updated')});

        // ui-configuration has changed, we need to reload the config
        loadConfig();

        onClose();
      },
      (err) => {
        showError(err);
        setIsLoading(false);
      }
    );
  }

  return (
    <Modal className="TelemetrySettings" open onClose={onClose}>
      <Modal.Header>{t('telemetry.header')}</Modal.Header>
      <Modal.Content>
        <p>{t('telemetry.text')}</p>
        <div className="options">
          <LabeledInput
            type="checkbox"
            label={
              <>
                <h2>{t('telemetry.enable')}</h2>
                <p>{t('telemetry.info')}</p>
              </>
            }
            checked={telemetryEnabled}
            onChange={(evt) => setTelemetryEnabled(evt.target.checked)}
          />
        </div>
        <p>
          <b>{t('telemetry.respectPrivacy')} </b>
          {t('telemetry.personalData')}{' '}
          <DocsLink location="self-managed/optimize-deployment/configuration/telemetry/">
            {t('common.documentation')}
          </DocsLink>{' '}
          {t('telemetry.orView', {
            policy: t('telemetry.privacyPolicy'),
            link: 'https://camunda.com/legal/privacy/',
          })}
        </p>
      </Modal.Content>
      <Modal.Footer
        primaryButtonText={t('common.save')}
        primaryButtonDisabled={isLoading}
        onRequestSubmit={submit}
        onRequestClose={onClose}
        secondaryButtonText={t('common.cancel')}
      />
    </Modal>
  );
}

export default withErrorHandling(TelemetrySettings);
