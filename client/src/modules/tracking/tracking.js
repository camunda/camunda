/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import mixpanel from 'mixpanel-browser';
import {getMixpanelConfig} from 'config';

export async function initTracking() {
  if (await isEnabled()) {
    mixpanel.init(config['token'], {
      api_host: config['apiHost'],
      batch_requests: true,
    });

    mixpanel.register({
      Product: 'optimize',
      Organization: config['organizationId'],
      Development: process.env.NODE_ENV === 'development',
    });
  }
}

let config;

async function isEnabled() {
  return config ? config.enabled : await initialize().then(() => config.enabled);
}

async function initialize() {
  return getMixpanelConfig().then((value) => (config = value));
}

export async function track(name, props) {
  if (await isEnabled()) {
    mixpanel.track(name, props);
  }
}

export function trackPageView(viewName) {
  track('Page view', {
    Path: window.location.hash,
  }).then();
}
