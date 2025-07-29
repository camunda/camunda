/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import js from '@eslint/js';
import tsPlugin from '@typescript-eslint/eslint-plugin';
import tsParser from '@typescript-eslint/parser';
import reactRefreshPlugin from 'eslint-plugin-react-refresh';
import testingLibraryPlugin from 'eslint-plugin-testing-library';
import licenseHeaderPlugin from 'eslint-plugin-license-header';
import reactHooksPlugin from 'eslint-plugin-react-hooks';
import tanstackPlugin from '@tanstack/eslint-plugin-query';
import prettierConfig from 'eslint-config-prettier';
import prettierPlugin from 'eslint-plugin-prettier';
import globals from 'globals';
import vitestPlugin from '@vitest/eslint-plugin';

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
  js.configs.recommended,
  prettierConfig,

  {
    plugins: {
      prettier: prettierPlugin,
    },
    rules: {
      'prettier/prettier': 'error',
    },
  },

  {
    files: files.node,
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      parser: tsParser,
      globals: {
        ...globals.node,
        ...globals.es2022,
      },
    },
  },

  {
    files: files.browser,
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      parser: tsParser,
      parserOptions: {
        ecmaFeatures: {
          jsx: true,
        },
        project: './tsconfig.json',
      },
      globals: {
        ...globals.browser,
        ...globals.es2022,
        React: false,
      },
    },
  },

  {
    files: files.browser,
    plugins: {
      'react-refresh': reactRefreshPlugin,
    },
    rules: {
      'react-refresh/only-export-components': [
        'error',
        {
          allowConstantExport: true,
        },
      ],
    },
  },

  {
    files: files.browser,
    plugins: {
      'react-hooks': reactHooksPlugin,
    },
    rules: {
      ...reactHooksPlugin.configs.recommended.rules,
    },
  },

  {
    files: files.browser,
    plugins: {
      '@tanstack/query': tanstackPlugin,
    },
    rules: {
      ...tanstackPlugin.configs.recommended.rules,
    },
  },

  {
    files: files.test,
    ...vitestPlugin.configs.recommended,
    languageOptions: {
      globals: {
        ...vitestPlugin.environments.env.globals,
        ...globals.node,
        ...globals.es2022,
      },
    },
  },

  {
    files: files.test,
    plugins: {
      'testing-library': testingLibraryPlugin,
    },
    rules: {
      ...testingLibraryPlugin.configs.react.rules,
      'testing-library/no-debugging-utils': 'error',
      'testing-library/no-node-access': 'off',
    },
  },

  {
    plugins: {
      '@typescript-eslint': tsPlugin,
    },
    rules: {
      ...tsPlugin.configs.recommended.rules,
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          args: 'all',
          argsIgnorePattern: '^_',
          caughtErrors: 'all',
          caughtErrorsIgnorePattern: '^_',
          destructuredArrayIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          ignoreRestSiblings: true,
        },
      ],
    },
  },

  {
    plugins: {
      'license-header': licenseHeaderPlugin,
    },
    rules: {
      'license-header/header': ['error', './resources/license-header.js'],
    },
  },

  {
    rules: {
      'no-duplicate-imports': 'error',
    },
  },

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
