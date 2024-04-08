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

export type UiConfig = {
  emailEnabled: boolean;
  sharingEnabled: boolean;
  metadataTelemetryEnabled: boolean;
  settingsManuallyConfirmed: boolean;
  tenantsAvailable: boolean;
  optimizeVersion: string;
  optimizeDocsVersion: string;
  optimizeProfile: 'platform' | 'cloud' | 'ccsm';
  enterpriseMode: boolean;
  webappsEndpoints: WebappEndpoints;
  webappsLinks: WebappLinks;
  webhooks: string[];
  mixpanel: MixpanelConfig;
  logoutHidden: boolean;
  exportCsvLimit: number;
  maxNumDataSourcesForReport: number;
  onboarding: Onboarding;
  notificationsUrl: string;
  userSearchAvailable: boolean;
  optimizeDatabase: 'opensearch' | 'elasticsearch';
  userTaskAssigneeAnalyticsEnabled: boolean;
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

export function createAccessorFunction<T>(property: keyof UiConfig): () => Promise<T> {
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
export const getDocsVersion = createAccessorFunction<string>('optimizeDocsVersion');
export const getWebappEndpoints = createAccessorFunction<WebappEndpoints>('webappsEndpoints');
export const getWebappLinks = createAccessorFunction<WebappLinks>('webappsLinks');
export const getWebhooks = createAccessorFunction<string[]>('webhooks');
export const getMixpanelConfig = createAccessorFunction<MixpanelConfig>('mixpanel');
export const getOptimizeProfile =
  createAccessorFunction<UiConfig['optimizeProfile']>('optimizeProfile');
export const isLogoutHidden = createAccessorFunction<boolean>('logoutHidden');
export const getExportCsvLimit = createAccessorFunction<number>('exportCsvLimit');
export const getMaxNumDataSourcesForReport = createAccessorFunction<number>(
  'maxNumDataSourcesForReport'
);
export const isEnterpriseMode = createAccessorFunction<boolean>('enterpriseMode');
export const getOnboardingConfig = createAccessorFunction<Onboarding>('onboarding');
export const getNotificationsUrl = createAccessorFunction<string>('notificationsUrl');
export const isUserSearchAvailable = createAccessorFunction<boolean>('userSearchAvailable');
export const getOptimizeDatabase =
  createAccessorFunction<UiConfig['optimizeDatabase']>('optimizeDatabase');
export const getUserTaskAssigneeAnalyticsEnabled = createAccessorFunction<boolean>(
  'userTaskAssigneeAnalyticsEnabled'
);

export {default as newReport} from './newReport.json';
