/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {App} from './App';
import './index.scss';
import 'dmn-js/dist/assets/dmn-js-decision-table.css';
import 'dmn-js/dist/assets/dmn-js-shared.css';
import 'dmn-js/dist/assets/dmn-js-drd.css';
import 'dmn-js/dist/assets/dmn-js-literal-expression.css';
import 'dmn-js/dist/assets/dmn-font/css/dmn.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import {tracking} from 'modules/tracking';
import {createRoot} from 'react-dom/client';

tracking.loadAnalyticsToWillingUsers().then(() => {
  const rootElement = document.getElementById('root');
  if (rootElement !== null) {
    const root = createRoot(rootElement);
    root.render(<App />);
  }
});
