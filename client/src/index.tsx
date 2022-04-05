/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import React from 'react';
import {render} from 'react-dom';
import {App} from './App';
import '@camunda-cloud/common-ui/dist/common-ui/common-ui.css';
import {tracking} from 'modules/tracking';
import './index.scss';

tracking.loadAnalyticsToWillingUsers().then(() => {
  render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
    document.querySelector('#root'),
  );
});
