/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {App} from './App';
// @ts-ignore - CSS imports
import './index.scss';
// @ts-ignore - CSS imports
import 'dmn-js/dist/assets/dmn-js-decision-table.css';
// @ts-ignore - CSS imports
import 'dmn-js/dist/assets/dmn-js-shared.css';
// @ts-ignore - CSS imports
import 'dmn-js/dist/assets/dmn-js-drd.css';
// @ts-ignore - CSS imports
import 'dmn-js/dist/assets/dmn-js-literal-expression.css';
// @ts-ignore - CSS imports
import 'dmn-js/dist/assets/dmn-font/css/dmn.css';
// @ts-ignore - CSS imports
import 'bpmn-js/dist/assets/bpmn-js.css';
import {tracking} from 'modules/tracking';
import {createRoot} from 'react-dom/client';

function enableMockingForDevEnv(): Promise<void> {
  return new Promise((resolve) => {
    if (
      import.meta.env.DEV ||
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
