import js from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.ts'],
    ignores: ['dist/**'],
    languageOptions: {
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
        project: false,
      },
    },
    rules: {
      'no-unused-vars': 'off', // handled by TS
      '@typescript-eslint/no-unused-vars': ['warn', {argsIgnorePattern: '^_'}],
      'no-undef': 'off',
      'no-console': 'off',
      'prefer-const': 'warn',
    },
  },
  {
    files: ['generated/**/*.ts'],
    rules: {
      // Generated code leniency (can tighten later)
      '@typescript-eslint/no-explicit-any': 'off',
      // Allow intentionally empty catch blocks used to swallow logging failures
      'no-empty': ['error', {allowEmptyCatch: true}],
    },
  },
);
