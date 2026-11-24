/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getStage} from './getStage';

const STAGE_ENV = getStage(window.location.host);

function injectScript(src: string): Promise<void> {
  return new Promise((resolve) => {
    const scriptElement = document.createElement('script');

    scriptElement.src = src;

    document.head.appendChild(scriptElement);

    setTimeout(resolve, 1000);

    scriptElement.onload = () => {
      resolve();
    };
    scriptElement.onerror = () => {
      resolve();
    };
  });
}

class Tracking {
  #isTrackingSupported = () => {
    return (
      !import.meta.env.DEV &&
      ['prod', 'int'].includes(STAGE_ENV) &&
      window.clientConfig?.organizationId
    );
  };

  #loadOsano = (): Promise<void> => {
    return new Promise((resolve) => {
      if (STAGE_ENV === 'int') {
        return injectScript(import.meta.env.VITE_OSANO_INT_ENV_URL).then(
          resolve,
        );
      }

      if (STAGE_ENV === 'prod') {
        return injectScript(import.meta.env.VITE_OSANO_PROD_ENV_URL).then(
          resolve,
        );
      }

      return resolve();
    });
  };

  loadAnalyticsToWillingUsers(): Promise<void> {
    if (!this.#isTrackingSupported()) {
      console.warn('Tracking is not supported for this environment');
      return Promise.resolve();
    }

    return this.#loadOsano();
  }
}

const tracking = new Tracking();

export {tracking};
