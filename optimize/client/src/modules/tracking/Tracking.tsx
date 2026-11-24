/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';

import {getAnalyticsConfig} from 'config';

import './Tracking.scss';

export default function Tracking() {
  useEffect(() => {
    (async () => {
      const {enabled, osanoScriptUrl} = await getAnalyticsConfig();

      if (enabled && osanoScriptUrl) {
        await loadOsanoScript(osanoScriptUrl);
      }
    })();
  }, []);

  return null;
}

async function loadOsanoScript(osanoScriptUrl: string) {
  return new Promise<void>((resolve) => {
    const osanoScriptElement = document.createElement('script');
    osanoScriptElement.src = osanoScriptUrl;
    document.head.appendChild(osanoScriptElement);
    osanoScriptElement.onload = () => resolve();
  });
}
