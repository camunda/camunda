/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useContext} from 'react';

import {UiConfig, configContext} from 'config';

export default function useUiConfig(): UiConfig {
  const contextValue = useContext(configContext);

  if (!contextValue) {
    throw new Error('useUiConfig has to be used within <ConfigProvider>');
  }

  return contextValue.config;
}

export function useLoadConfig() {
  const contextValue = useContext(configContext);

  if (!contextValue) {
    throw new Error('useUiConfig has to be used within <ConfigProvider>');
  }

  return contextValue.loadConfig;
}
