/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Mixpanel} from 'mixpanel-browser';

/* istanbul ignore file */

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
      organizationId?: null | string;
      clusterId?: null | string;
      canLogout?: null | boolean;
      isLoginDelegated?: null | boolean;
      mixpanelToken?: null | string;
      mixpanelAPIHost?: null | string;
      tasklistUrl?: null | string;
      resourcePermissionsEnabled?: boolean;
      multiTenancyEnabled?: boolean;
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
  }

  namespace NodeJS {
    interface ProcessEnv {
      REACT_APP_DEV_ENV_URL: string;
      REACT_APP_INT_ENV_URL: string;
      REACT_APP_PROD_ENV_URL: string;
      REACT_APP_OSANO_INT_ENV_URL: string;
      REACT_APP_OSANO_PROD_ENV_URL: string;
      REACT_APP_MIXPANEL_TOKEN: string;
      REACT_APP_CUES_HOST: string;
    }
  }
}
