/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get} from 'request';
import {showError} from 'notifications';

let config;
const awaiting = [];

export async function loadConfig() {
  try {
    const response = await get('api/ui-configuration');
    config = await response.json();

    awaiting.forEach((cb) => cb(config));
    awaiting.length = 0;
  } catch (e) {
    showError(e);
  }
}
loadConfig();

function createAccessorFunction(property) {
  return async function () {
    if (config) {
      return config[property];
    }

    return new Promise((resolve) => {
      awaiting.push((config) => resolve(config[property]));
    });
  };
}

export const isEmailEnabled = createAccessorFunction('emailEnabled');
export const isSharingEnabled = createAccessorFunction('sharingEnabled');
export const isMetadataTelemetryEnabled = createAccessorFunction('metadataTelemetryEnabled');
export const areSettingsManuallyConfirmed = createAccessorFunction('settingsManuallyConfirmed');
export const areTenantsAvailable = createAccessorFunction('tenantsAvailable');
export const getOptimizeVersion = createAccessorFunction('optimizeVersion');
export const getWebappEndpoints = createAccessorFunction('webappsEndpoints');
export const getHeader = createAccessorFunction('header');
export const getWebhooks = createAccessorFunction('webhooks');
export const getMixpanelConfig = createAccessorFunction('mixpanel');
export const getOptimizeProfile = createAccessorFunction('optimizeProfile');
export const isLogoutHidden = createAccessorFunction('logoutHidden');
export const getExportCsvLimit = createAccessorFunction('exportCsvLimit');
export const isEnterpriseMode = createAccessorFunction('enterpriseMode');
export const getOnboardingConfig = createAccessorFunction('onboarding');

export {default as newReport} from './newReport.json';
