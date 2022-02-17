/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import ReactDOM from 'react-dom';
import {App} from './App';
import './index.css';
import '@camunda-cloud/common-ui/dist/common-ui/common-ui.css';
import 'dmn-js/dist/assets/dmn-js-decision-table.css';
import 'dmn-js/dist/assets/dmn-js-shared.css';
import 'dmn-js/dist/assets/dmn-js-drd.css';
import 'dmn-js/dist/assets/dmn-js-literal-expression.css';
import 'dmn-js/dist/assets/dmn-font/css/dmn.css';
import {startMocking} from 'modules/mock-server/browser';
import {tracking} from 'modules/tracking';

if (
  process.env.NODE_ENV === 'development' ||
  window.location.host.match(/camunda\.cloud$/) !== null
) {
  startMocking();
}

tracking.loadPermissions().then(() => {
  ReactDOM.render(<App />, document.getElementById('root'));
});
