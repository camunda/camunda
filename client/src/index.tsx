/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {createRoot} from 'react-dom/client';
import {App} from './App';
import {tracking} from 'modules/tracking';
import './index.scss';
import {StrictMode} from 'react';
import {loadMonaco} from 'loadMonaco';

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

Promise.all([
  tracking.loadAnalyticsToWillingUsers(),
  mock(),
  loadMonaco(),
]).then(() => {
  root.render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});
