/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import js from '@eslint/js';
import licenseHeader from 'eslint-plugin-license-header';
import {defineConfig, globalIgnores} from 'eslint/config';
import ts from 'typescript-eslint';

export default defineConfig(
  globalIgnores(['reports']),
  js.configs.recommended,
  {
    files: ['**/*.ts'],
    languageOptions: {parserOptions: {projectService: true}},
    extends: [
      ts.configs.recommendedTypeChecked,
      ts.configs.stylisticTypeChecked,
    ],
  },
  {
    plugins: {'license-header': licenseHeader},
    rules: {
      'license-header/header': ['error', './license-header.txt'],
    },
  },
);
