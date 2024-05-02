/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* istanbul ignore file */

import {createRoot} from 'react-dom/client';
import {App} from './App';
import {tracking} from 'modules/tracking';
import './index.scss';
import {StrictMode} from 'react';

function mock(): Promise<void> {
  return new Promise((resolve) => {
    if (
      import.meta.env.DEV ||
      window.location.host.match(/camunda\.cloud$/) !== null
    ) {
      import('modules/mockServer/startBrowserMocking').then(
        ({startBrowserMocking}) => {
          startBrowserMocking();
          resolve();
        },
      );
    } else {
      resolve();
    }
  });
}
const container = document.querySelector('#root');
const root = createRoot(container!);

Promise.all([tracking.loadAnalyticsToWillingUsers(), mock()]).finally(() => {
  root.render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});
