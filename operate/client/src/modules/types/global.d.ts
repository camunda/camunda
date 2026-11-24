/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* istanbul ignore file */

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
      tasklistUrl?: null | string;
      /**
       * @deprecated The C8 API does not expose permissions with resources.
       * Therefore, permissions should not be checked proactively on the client.
       * Let users try the action and surface missing permissions errors instead.
       */
      resourcePermissionsEnabled?: boolean;
      multiTenancyEnabled?: boolean;
      waitStatesEnabled?: boolean;
      isNavV2Enabled?: boolean;
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
  }

  namespace NodeJS {
    interface ProcessEnv {
      REACT_APP_DEV_ENV_URL: string;
      REACT_APP_INT_ENV_URL: string;
      REACT_APP_PROD_ENV_URL: string;
      REACT_APP_OSANO_INT_ENV_URL: string;
      REACT_APP_OSANO_PROD_ENV_URL: string;
    }
  }
}
