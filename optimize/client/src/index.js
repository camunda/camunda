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
import {IS_NAV_V2_ENABLED} from 'feature-flags';

import App from './App';

// re-apply any route stashed before the logout/session-expiry -> login cycle, before the hash
// router mounts (ADR-0038:
// https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md)
restorePostLoginRedirect();

// On the body rather than the root, because modals render through a portal into the body.
document.body.classList.toggle('optimize-nav-v2', IS_NAV_V2_ENABLED);

// Dynamic so the stylesheet stays off the legacy path; a static import is hoisted past the flag.
if (IS_NAV_V2_ENABLED) {
  import('@camunda/design-system/styles.css');
}

const root = createRoot(document.getElementById('root'));
root.render(<App />);
