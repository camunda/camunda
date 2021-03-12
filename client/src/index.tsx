/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import ReactDOM from 'react-dom';
import {App} from './App';
import './index.css';
import '@camunda-cloud/common-ui/dist/common-ui/common-ui.css';
import {worker} from 'modules/mock-server/browser';

if (process.env.NODE_ENV === 'development') {
  worker.start();
}

ReactDOM.render(<App />, document.getElementById('root'));
