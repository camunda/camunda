/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
