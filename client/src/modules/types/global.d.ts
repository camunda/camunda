/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Mixpanel} from 'mixpanel-browser';

type Appcues = {
  debug: () => void;
  page: () => void;
  identify: (
    userId: string,
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
      canLogout?: boolean;
      isLoginDelegated?: boolean;
      organizationId?: null | string;
      clusterId?: null | string;
      mixpanelToken?: null | string;
      mixpanelAPIHost?: null | string;
      isResourcePermissionsEnabled?: boolean;
      isMultiTenancyEnabled?: boolean;
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
