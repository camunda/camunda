/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect, useState} from 'react';
import {useLocation} from 'react-router-dom';
import mixpanel from 'mixpanel-browser';

import {getMixpanelConfig, getOptimizeVersion} from 'config';
import {withUser} from 'HOC';

import './Tracking.scss';

export function Tracking({getUser}) {
  const location = useLocation();
  const [enabled, setEnabled] = useState(false);

  useEffect(() => {
    if (enabled) {
      mixpanel.track('optimize:pageView', {
        path: window.location.hash,
      });
    }
  }, [location, enabled]);

  useEffect(() => {
    (async () => {
      const {enabled, token, apiHost, organizationId, osanoScriptUrl, stage, clusterId} =
        await getMixpanelConfig();
      if (!enabled || !osanoScriptUrl || !(await isOsanoAnalyticsEnabled(osanoScriptUrl))) {
        return;
      }

      mixpanel.init(token, {
        api_host: apiHost,
        batch_requests: true,
        debug: process.env.NODE_ENV === 'development',
      });

      const user = await getUser();
      mixpanel.identify(user.id);
      mixpanel.register({
        userId: user.id,
        orgId: organizationId,
        stage: stage,
        clusterId: clusterId,
        product: 'optimize',
        version: await getOptimizeVersion(),
        development: process.env.NODE_ENV === 'development',
        frontend: true,
        // additional project group properties
        org_id: organizationId,
        cluster_id: clusterId,
      });
      setEnabled(enabled);
    })();
  }, [getUser]);

  return null;
}

async function isOsanoAnalyticsEnabled(osanoScriptUrl) {
  return new Promise((resolve) => {
    const osanoScriptElement = document.createElement('script');
    osanoScriptElement.src = osanoScriptUrl;
    document.head.appendChild(osanoScriptElement);

    osanoScriptElement.onload = () => {
      resolve(window.Osano?.cm?.analytics);
    };
  });
}

export default withUser(Tracking);
