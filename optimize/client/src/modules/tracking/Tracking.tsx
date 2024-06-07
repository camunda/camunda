/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {useLocation} from 'react-router-dom';

import {getMixpanelConfig, getOptimizeVersion} from 'config';
import {useUser} from 'hooks';

import './Tracking.scss';

declare const window: {
  mixpanel: any;
  Osano: any;
} & Window;

let trackingEnabled = false;

export function track(eventName: string, properties?: Record<string, unknown>) {
  if (trackingEnabled) {
    window.mixpanel.track(`optimize:${eventName}`, properties);
  }
}

export default function Tracking() {
  const location = useLocation();
  const [initialized, setInitialized] = useState(false);
  const {user} = useUser();

  useEffect(() => {
    if (initialized) {
      track('pageView', {
        path: window.location.hash,
      });
    }
  }, [location, initialized]);

  useEffect(() => {
    (async () => {
      const {enabled, osanoScriptUrl} = await getMixpanelConfig();

      if (enabled && osanoScriptUrl) {
        await loadOsanoScript(osanoScriptUrl);

        window.Osano?.cm?.addEventListener(
          'osano-cm-consent-saved',
          async ({ANALYTICS}: {ANALYTICS: string}) => {
            if (ANALYTICS === 'ACCEPT') {
              await initMixpanel();
              setInitialized(true);
              trackingEnabled = true;
            }
          }
        );
      }
    })();
  }, []);

  useEffect(() => {
    if (initialized && user?.id) {
      window.mixpanel.identify(user.id);
      window.mixpanel.register({
        userId: user.id,
      });
    }
  }, [initialized, user?.id]);

  return null;
}

async function initMixpanel() {
  const {token, apiHost, organizationId, stage, clusterId} = await getMixpanelConfig();

  window.mixpanel.init(token, {
    api_host: apiHost,
    batch_requests: true,
    debug: process.env.NODE_ENV === 'development',
  });

  window.mixpanel.register({
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
}

async function loadOsanoScript(osanoScriptUrl: string) {
  return new Promise<void>((resolve) => {
    const osanoScriptElement = document.createElement('script');
    osanoScriptElement.src = osanoScriptUrl;
    document.head.appendChild(osanoScriptElement);
    osanoScriptElement.onload = () => resolve();
  });
}
