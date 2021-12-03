/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect, useState} from 'react';
import {useLocation} from 'react-router-dom';
import mixpanel from 'mixpanel-browser';

import {getMixpanelConfig} from 'config';

import './Tracking.scss';

export default function Tracking() {
  const location = useLocation();
  const [enabled, setEnabled] = useState(false);

  useEffect(() => {
    if (enabled) {
      mixpanel.track('Page view', {
        Path: window.location.hash,
      });
    }
  }, [location, enabled]);

  useEffect(() => {
    (async () => {
      const {enabled, token, apiHost, organizationId, osanoScriptUrl} = await getMixpanelConfig();
      if (!enabled || !osanoScriptUrl) {
        return;
      }

      function initMixpanel() {
        mixpanel.init(token, {
          api_host: apiHost,
          batch_requests: true,
        });

        mixpanel.register({
          product: 'optimize',
          organization: organizationId,
          development: process.env.NODE_ENV === 'development',
        });
      }

      const osanoScriptElement = document.createElement('script');
      osanoScriptElement.src = osanoScriptUrl;
      document.head.appendChild(osanoScriptElement);

      osanoScriptElement.onload = function () {
        if (analyticsEnabled()) {
          initMixpanel();
          setEnabled(enabled);
        }
      };
    })();
  }, []);

  return null;
}

function analyticsEnabled() {
  return window.Osano?.cm?.analytics;
}
