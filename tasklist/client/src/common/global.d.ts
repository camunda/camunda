/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Mixpanel} from 'mixpanel-browser';

type Appcues = {
  debug: () => void;
  page: () => void;
  identify: (
    username: string,
    properties?: {[property: string]: unknown},
  ) => void;
  track: (
    eventName: string,
    properties?: {[eventProperty: string]: unknown},
  ) => void;
};

export declare global {
  interface Window {
    clientConfig?: {
      isEnterprise?: boolean;
      contextPath?: string;
      baseName?: string;
      canLogout?: boolean;
      isLoginDelegated?: boolean;
      organizationId?: null | string;
      clusterId?: null | string;
      mixpanelToken?: null | string;
      mixpanelAPIHost?: null | string;
      isResourcePermissionsEnabled?: boolean;
      isMultiTenancyEnabled?: boolean;
      maxRequestSize?: number;
      clientMode?: 'v1' | 'v2';
    };
    Osano?: {
      cm?: {
        analytics: boolean;
        showDrawer: (arg: string) => void;
        addEventListener: (
          eventType: string,
          callback: (arg: {ANALYTICS: 'ACCEPT' | 'DENY'}) => void,
        ) => void;
      };
    };
    Appcues?: Appcues;
    mixpanel?: Mixpanel;
    toggleDevtools?: () => void;
  }

  namespace NodeJS {
    interface ProcessEnv {
      VITE_DEV_ENV_URL: string;
      VITE_INT_ENV_URL: string;
      VITE_PROD_ENV_URL: string;
      VITE_OSANO_DEV_ENV_URL: string;
      VITE_OSANO_INT_ENV_URL: string;
      VITE_OSANO_PROD_ENV_URL: string;
      VITE_MIXPANEL_TOKEN: string;
      VITE_CUES_HOST: string;
    }
  }
}
