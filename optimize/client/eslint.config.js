import eslintJs from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';
import playwright from 'eslint-plugin-playwright';
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
      },
    },
    plugins: {
      'license-header': licenseHeader,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
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
  },
  {
    files: ['e2e/**/*.ts'],
    ...playwright.configs['flat/recommended'],
    rules: {
      ...playwright.configs['flat/recommended'].rules,
      ...reactHooksOff(),
      'playwright/no-wait-for-timeout': 'error',
      'playwright/no-wait-for-selector': 'error',
      'playwright/no-wait-for-navigation': 'error',
      'playwright/no-networkidle': 'error',
      'playwright/no-force-option': 'error',
      'playwright/no-element-handle': 'error',
      'playwright/no-eval': 'error',
      'playwright/no-page-pause': 'error',
      'playwright/no-skipped-test': 'error',
      'playwright/no-focused-test': 'error',
      'playwright/no-conditional-in-test': 'error',
      'playwright/no-conditional-expect': 'error',
      'playwright/prefer-web-first-assertions': 'error',
      'playwright/no-useless-await': 'error',
      'playwright/missing-playwright-await': 'error',
    },
  },
  {
    files: ['e2e/tests/**/*.ts', 'e2e/visual/**/*.ts'],
    rules: {
      // Locators beyond roles, labels and test ids belong in page objects.
      'playwright/no-raw-locators': 'error',
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: '@playwright/test',
              message: "Import test and expect from '../fixtures' to get the shared fixtures.",
            },
          ],
        },
      ],
    },
  }
);

// Playwright fixtures call their parameter `use`, which the React hooks rules mistake for a hook.
function reactHooksOff() {
  return Object.fromEntries(
    Object.keys(reactHooks.configs.recommended.rules).map((rule) => [rule, 'off'])
  );
}
