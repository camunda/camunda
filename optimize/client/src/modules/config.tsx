/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode, createContext, useEffect, useState} from 'react';
import {Loading} from '@carbon/react';

import {get, ErrorResponse} from 'request';
import {showError} from 'notifications';

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
  tenantsAvailable: boolean;
  optimizeVersion: string;
  optimizeDocsVersion: string;
  optimizeProfile: 'cloud' | 'ccsm';
  enterpriseMode: boolean;
  webappsLinks: WebappLinks;
  mixpanel: MixpanelConfig;
  logoutHidden: boolean;
  exportCsvLimit: number;
  maxNumDataSourcesForReport: number;
  onboarding: Onboarding;
  notificationsUrl: string;
  userSearchAvailable: boolean;
  optimizeDatabase: 'opensearch' | 'elasticsearch';
  userTaskAssigneeAnalyticsEnabled: boolean;
  licenseType: 'production' | 'saas' | 'unknown';
  validLicense: boolean;
  commercial: boolean;
  expiresAt: string | null;
};

let globalConfig: UiConfig;
const awaiting: Array<(config: Record<string, unknown>) => void> = [];

interface ConfigContextProps {
  config: UiConfig;
  loadConfig: () => Promise<void>;
}

export const configContext = createContext<ConfigContextProps | null>(null);
export function ConfigProvider({children}: {children: ReactNode}): JSX.Element {
  const [config, setConfig] = useState<UiConfig>();

  const loadConfig = async () => {
    try {
      const response = await get('api/ui-configuration');
      const config = await response.json();

      setConfig(config);
      globalConfig = config;
      awaiting.forEach((cb) => cb(config));
      awaiting.length = 0;
    } catch (err) {
      showError(err as ErrorResponse);
    }
  };

  useEffect(() => {
    loadConfig();
  }, []);

  if (!config) {
    return <Loading />;
  }

  return <configContext.Provider value={{config, loadConfig}}>{children}</configContext.Provider>;
}

export function createAccessorFunction<T>(property: keyof UiConfig): () => Promise<T> {
  return async function (): Promise<T> {
    if (globalConfig) {
      return globalConfig[property] as T;
    }

    return new Promise((resolve) => {
      awaiting.push((config) => resolve(config[property] as T));
    });
  };
}

export const isEmailEnabled = createAccessorFunction<boolean>('emailEnabled');
export const isSharingEnabled = createAccessorFunction<boolean>('sharingEnabled');
export const areTenantsAvailable = createAccessorFunction<boolean>('tenantsAvailable');
export const getOptimizeVersion = createAccessorFunction<string>('optimizeVersion');
export const getDocsVersion = createAccessorFunction<string>('optimizeDocsVersion');
export const getWebappLinks = createAccessorFunction<WebappLinks>('webappsLinks');
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
