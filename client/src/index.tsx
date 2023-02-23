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

function mock(): Promise<void> {
  return new Promise((resolve) => {
    if (
      process.env.NODE_ENV === 'development' ||
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

Promise.all([tracking.loadAnalyticsToWillingUsers(), mock()]).then(() => {
  // Add StrictMode again when this is fixed https://github.com/apollographql/apollo-client/issues/9819
  root.render(<App />);
});
