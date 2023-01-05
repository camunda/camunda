/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {App} from './App';
import './index.scss';
import '@camunda-cloud/common-ui/dist/common-ui/common-ui.css';
import 'dmn-js/dist/assets/dmn-js-decision-table.css';
import 'dmn-js/dist/assets/dmn-js-shared.css';
import 'dmn-js/dist/assets/dmn-js-drd.css';
import 'dmn-js/dist/assets/dmn-js-literal-expression.css';
import 'dmn-js/dist/assets/dmn-font/css/dmn.css';
import {tracking} from 'modules/tracking';
import {createRoot} from 'react-dom/client';

function enableMockingForDevEnv(): Promise<void> {
  return new Promise((resolve) => {
    if (
      process.env.NODE_ENV === 'development' ||
      window.location.host.match(/camunda\.cloud$/) !== null
    ) {
      import('modules/mock-server/browser').then(({startMocking}) => {
        startMocking();
        resolve();
      });
    } else {
      resolve();
    }
  });
}

Promise.all([
  tracking.loadAnalyticsToWillingUsers(),
  enableMockingForDevEnv(),
]).then(() => {
  const rootElement = document.getElementById('root');
  if (rootElement !== null) {
    const root = createRoot(rootElement);
    root.render(<App />);
  }
});
