/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  baseConfig,
  typescriptConfig,
  reactConfig,
  testingConfig,
  tanstackQueryConfig,
  licenseConfig,
} from '@camunda/lint-config/eslint';

const files = {
  browser: ['src/**/*.{js,jsx,ts,tsx}'],
  test: ['src/**/*.test.ts', 'src/**/*.test.tsx', 'src/setupTests.ts'],
  node: [
    'e2e/**/*.{js,jsx,ts,tsx}',
    'playwright.config.ts',
    'vite.config.ts',
    'stylelint.config.js',
    'stylelint-plugin-license-header.js',
    'renameProdIndex.mjs',
  ],
};

export default [
  ...baseConfig,

  ...typescriptConfig({
    browserFiles: files.browser,
    testFiles: files.test,
    nodeFiles: files.node,
    tsconfigRootDir: import.meta.dirname,
    tsProjects: ['./tsconfig.app.json', './tsconfig.vitest.json'],
  }),

  ...reactConfig({browserFiles: files.browser, testFiles: files.test}),
  ...testingConfig({testFiles: files.test}),
  ...tanstackQueryConfig({browserFiles: files.browser}),
  ...licenseConfig({licenseHeaderPath: './resources/license-header.js'}),

  {
    ignores: [
      'dist/*',
      'node_modules/*',
      'build/*',
      'target/*',
      'coverage/*',
      'src/fonts/*',
      'test-results/*',
      'playwright-report/*',
      'playwright/.cache/*',
      '.eslintcache',
      '.env',
      '.prettierignore',
      'resources/license-header.js',
    ],
  },
];
