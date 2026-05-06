/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {StorybookConfig} from '@storybook/react-vite';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';
import {fileURLToPath} from 'url';

const config: StorybookConfig = {
  stories: ['../src/**/*.stories.@(ts|tsx)'],
  addons: ['@storybook/addon-docs'],
  framework: {
    name: '@storybook/react-vite',
    options: {},
  },
  viteFinal: async (config) => {
    config.plugins ??= [];
    config.plugins.push(tailwindcss());
    config.resolve ??= {};
    config.resolve.alias = {
      ...(config.resolve.alias as object),
      '@': path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../src'),
    };
    return config;
  },
};

export default config;
