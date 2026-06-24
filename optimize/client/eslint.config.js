import eslintJs from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';
import testcafe from 'eslint-plugin-testcafe';
import licenseHeader from 'eslint-plugin-license-header';
import prettier from 'eslint-plugin-prettier';
import eslintConfigPrettier from 'eslint-config-prettier';

export default tseslint.config(
  eslintJs.configs.recommended,
  ...tseslint.configs.recommended,
  eslintConfigPrettier,
  {
    ignores: [
      'dist/**',
      '**/__mocks__/**',
      '**/node_modules/**',
      '.node/**',
      'public/**',
      'resources/**',
      'eslint.config.js',
      'index.html',
    ],
  },
  {
    files: ['**/*.{ts,tsx,js,jsx}'],
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.jest,
        ...globals.node,
        flushPromises: 'readonly',
        fixture: false,
        test: false,
      },
    },
    plugins: {
      'license-header': licenseHeader,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      testcafe,
      prettier,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['off', {allowConstantExport: true}],
      'license-header/header': ['error', './resources/license-header.js'],
      'prettier/prettier': ['warn', {endOfLine: 'auto'}],
      curly: 'error',
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
  }
);
