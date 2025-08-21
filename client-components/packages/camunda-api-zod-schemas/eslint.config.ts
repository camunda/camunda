import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import {defineConfig} from 'eslint/config';

export default defineConfig([
	{
		files: ['**/*.{js,mjs,cjs,ts,mts,cts}'],
		plugins: {js},
		extends: ['js/recommended'],
		languageOptions: {
			globals: globals.browser,
			parserOptions: {
				tsconfigRootDir: import.meta.dirname,
			},
		},
	},
	tseslint.configs.recommended,
	{
		rules: {
			'@typescript-eslint/no-unused-vars': 'off',
			'@typescript-eslint/no-empty-object-type': 'off',
		},
	},
]);
