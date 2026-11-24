/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getAnalyticsConfig} from 'config';

import './osano.scss';

function injectScript(src: string): Promise<void> {
  return new Promise((resolve) => {
    const scriptElement = document.createElement('script');

    scriptElement.src = src;
    document.head.appendChild(scriptElement);

    setTimeout(resolve, 1000);
    scriptElement.onload = () => resolve();
    scriptElement.onerror = () => resolve();
  });
}

async function loadOsano(): Promise<void> {
  const {enabled, osanoScriptUrl} = await getAnalyticsConfig();

  if (!enabled || !osanoScriptUrl) {
    return;
  }

  await injectScript(osanoScriptUrl);
}

export {loadOsano};
