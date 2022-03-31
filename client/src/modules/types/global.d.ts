/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

type Appcues = {
  debug: () => void;
  page: () => void;
  identify: (userId: string, properties?: {[property: string]: any}) => void;
  track: (
    eventName: string,
    properties?: {[eventProperty: string]: any}
  ) => void;
};

export declare global {
  interface Window {
    clientConfig?: {
      isEnterprise?: boolean;
      contextPath?: string;
      organizationId?: null | string;
      clusterId?: null | string;
      canLogout?: null | boolean;
      isLoginDelegated?: null | boolean;
      mixpanelToken?: null | string;
      mixpanelAPIHost?: null | string;
    };
    Osano?: {
      cm?: {
        analytics: boolean;
      };
    };
    Appcues?: Appcues;
  }

  type SortOrder = 'asc' | 'desc';

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
