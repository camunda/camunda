/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get, ErrorResponse} from 'request';
import {showError} from 'notifications';

type WebappEndpoints = {
  [key: string]: {
    endpoint: string;
    engineName: string;
  };
};

type WebappLinks = {
  [key: string]: string;
};

interface MixpanelConfig {
  enabled: boolean;
  apiHost: string;
  token: string;
  organizationId: string;
  osanoScriptUrl: string;
  stage: string;
  clusterId: string;
}

interface Onboarding {
  enabled: boolean;
  appCuesScriptUrl: string;
  orgId: string;
  clusterId: string;
  salesPlanType: string;
}

type Config = {
  emailEnabled: boolean;
  sharingEnabled: boolean;
  metadataTelemetryEnabled: boolean;
  settingsManuallyConfirmed: boolean;
  tenantsAvailable: boolean;
  optimizeVersion: string;
  optimizeProfile: 'platform' | 'cloud' | 'ccsm';
  enterpriseMode: boolean;
  webappsEndpoints: WebappEndpoints;
  webappsLinks: WebappLinks;
  webhooks: string[];
  mixpanel: MixpanelConfig;
  logoutHidden: boolean;
  exportCsvLimit: number;
  onboarding: Onboarding;
};

let config: Record<string, unknown>;
const awaiting: Array<(config: Record<string, unknown>) => void> = [];

export async function loadConfig(): Promise<void> {
  try {
    const response = await get('api/ui-configuration');
    config = await response.json();

    awaiting.forEach((cb) => cb(config));
    awaiting.length = 0;
  } catch (err) {
    showError(err as ErrorResponse);
  }
}

loadConfig();

function createAccessorFunction<T>(property: keyof Config): () => Promise<T> {
  return async function (): Promise<T> {
    if (config) {
      return config[property] as T;
    }

    return new Promise((resolve) => {
      awaiting.push((config) => resolve(config[property] as T));
    });
  };
}

export const isEmailEnabled = createAccessorFunction<boolean>('emailEnabled');
export const isSharingEnabled = createAccessorFunction<boolean>('sharingEnabled');
export const isMetadataTelemetryEnabled = createAccessorFunction<boolean>(
  'metadataTelemetryEnabled'
);
export const areSettingsManuallyConfirmed = createAccessorFunction<boolean>(
  'settingsManuallyConfirmed'
);
export const areTenantsAvailable = createAccessorFunction<boolean>('tenantsAvailable');
export const getOptimizeVersion = createAccessorFunction<string>('optimizeVersion');
export const getWebappEndpoints = createAccessorFunction<WebappEndpoints>('webappsEndpoints');
export const getWebappLinks = createAccessorFunction<WebappLinks>('webappsLinks');
export const getWebhooks = createAccessorFunction<string[]>('webhooks');
export const getMixpanelConfig = createAccessorFunction<MixpanelConfig>('mixpanel');
export const getOptimizeProfile =
  createAccessorFunction<Config['optimizeProfile']>('optimizeProfile');
export const isLogoutHidden = createAccessorFunction<boolean>('logoutHidden');
export const getExportCsvLimit = createAccessorFunction<number>('exportCsvLimit');
export const isEnterpriseMode = createAccessorFunction<boolean>('enterpriseMode');
export const getOnboardingConfig = createAccessorFunction<Onboarding>('onboarding');

export {default as newReport} from './newReport.json';
