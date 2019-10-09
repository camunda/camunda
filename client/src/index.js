/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import 'react-app-polyfill/ie11';

import React from 'react';
import ReactDOM from 'react-dom';

import '@ibm/plex/scss/ibm-plex.scss';
import './style.scss';
import 'polyfills';

import App from './App';

ReactDOM.render(<App />, document.getElementById('root'));
