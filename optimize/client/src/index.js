/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createRoot} from 'react-dom/client';

import './style.scss';
import 'polyfills';

import {restorePostLoginRedirect} from 'postLoginRedirect';

import App from './App';

// SPIKE (ADR-0036): re-apply the route stashed before a logout/expiry -> login cycle, before the
// hash router mounts so it picks up the restored hash.
restorePostLoginRedirect();

const root = createRoot(document.getElementById('root'));
root.render(<App />);
