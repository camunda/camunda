/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {defineConfig} from 'oxlint';
import tanstackQueryPlugin from '@tanstack/eslint-plugin-query';
import camundaDsPlugin from '@camunda/design-system/eslint-plugin';

const oc = 'apps/orchestration-cluster-webapp';
const ocSrc = `${oc}/src/**/*.{js,jsx,ts,tsx}`;
const ocTestFiles = [`${oc}/src/**/*.test.{ts,tsx}`, `${oc}/src/vitest-modules/**/*`];
const browserFiles = ['packages/**/*.{js,jsx,mjs,cjs,ts,tsx,mts,cts}', 'apps/**/*.{js,jsx,mjs,cjs,ts,tsx,mts,cts}'];
const nodeFiles = [
	'scripts/**/*.{js,mjs,cjs,ts,mts,cts}',
	'oxlint-rules/**/*.js',
	'prettier.config.js',
	'oxlint.config.ts',
	'packages/**/scripts/**/*.{js,mjs,cjs,ts,mts,cts}',
];

const reactHooksRules = {
	'react/rules-of-hooks': 'error',
	'react/exhaustive-deps': 'warn',
	'react/static-components': 'error',
	'react/use-memo': 'error',
	'react/preserve-manual-memoization': 'error',
	'react/incompatible-library': 'warn',
	'react/immutability': 'error',
	'react/globals': 'error',
	'react/refs': 'error',
	'react/set-state-in-effect': 'error',
	'react/error-boundaries': 'error',
	'react/purity': 'error',
	'react/set-state-in-render': 'error',
	'react/unsupported-syntax': 'warn',
} as const;

export default defineConfig({
	// Built-in rules only run where a matching override (or the root) enables their plugin. Enabling them here is free
	// because no category is on; `import` stays scoped below since it makes oxlint resolve the module graph.
	plugins: ['typescript', 'react', 'vitest'],
	categories: {
		correctness: 'off',
	},
	env: {
		builtin: true,
	},
	ignorePatterns: [
		'**/gen/**',
		'packages/**/dist/**',
		'apps/**/dist/**',
		'target/**',
		`${oc}/src/modules/svg/**`,
		'apps/**/playwright-report/**',
		'apps/**/test-results/**',
		'apps/**/test-artifacts/**',
		`${oc}/src/operate/shared/DecisionViewer/dmn-js.d.ts`,
	],
	jsPlugins: [
		'eslint-plugin-license-header',
		'@tanstack/eslint-plugin-query',
		{name: 'camunda-ds', specifier: '@camunda/design-system/eslint-plugin'},
		{name: 'local', specifier: './oxlint-rules/index.js'},
	],
	rules: {
		'no-array-constructor': 'error',
		'no-unused-expressions': 'error',
		'no-unused-vars': 'error',
		'typescript/ban-ts-comment': 'error',
		'typescript/no-duplicate-enum-values': 'error',
		'typescript/no-empty-object-type': 'error',
		'typescript/no-explicit-any': 'error',
		'typescript/no-extra-non-null-assertion': 'error',
		'typescript/no-misused-new': 'error',
		'typescript/no-namespace': 'error',
		'typescript/no-non-null-asserted-optional-chain': 'error',
		'typescript/no-require-imports': 'error',
		'typescript/no-this-alias': 'error',
		'typescript/no-unnecessary-type-constraint': 'error',
		'typescript/no-unsafe-declaration-merging': 'error',
		'typescript/no-unsafe-function-type': 'error',
		'typescript/no-wrapper-object-types': 'error',
		'typescript/prefer-as-const': 'error',
		'typescript/prefer-namespace-keyword': 'error',
		'typescript/triple-slash-reference': 'error',
		'typescript/no-array-delete': 'error',
		'typescript/no-for-in-array': 'error',
		'typescript/no-implied-eval': 'error',
		'typescript/no-misused-spread': 'error',
		'typescript/no-unsafe-unary-minus': 'error',
		'typescript/require-array-sort-compare': 'error',
		'for-direction': 'error',
		'no-async-promise-executor': 'error',
		'no-case-declarations': 'error',
		'no-compare-neg-zero': 'error',
		'no-cond-assign': 'error',
		'no-constant-binary-expression': 'error',
		'no-constant-condition': 'error',
		'no-control-regex': 'error',
		'no-debugger': 'error',
		'no-delete-var': 'error',
		'no-dupe-else-if': 'error',
		'no-duplicate-case': 'error',
		'no-empty': 'error',
		'no-empty-character-class': 'error',
		'no-empty-pattern': 'error',
		'no-empty-static-block': 'error',
		'no-ex-assign': 'error',
		'no-extra-boolean-cast': 'error',
		'no-fallthrough': 'error',
		'no-global-assign': 'error',
		'no-invalid-regexp': 'error',
		'no-irregular-whitespace': 'error',
		'no-loss-of-precision': 'error',
		'no-misleading-character-class': 'error',
		'no-nonoctal-decimal-escape': 'error',
		'no-prototype-builtins': 'error',
		'no-regex-spaces': 'error',
		'no-self-assign': 'error',
		'no-shadow-restricted-names': 'error',
		'no-sparse-arrays': 'error',
		'no-unassigned-vars': 'error',
		'no-unexpected-multiline': 'error',
		'no-unsafe-finally': 'error',
		'no-unsafe-optional-chaining': 'error',
		'no-unused-labels': 'error',
		'no-unused-private-class-members': 'error',
		'no-useless-backreference': 'error',
		'no-useless-catch': 'error',
		'no-useless-assignment': 'error',
		'no-useless-escape': 'error',
		'preserve-caught-error': 'error',
		'require-yield': 'error',
		'use-isnan': 'error',
		'valid-typeof': 'error',
	},
	overrides: [
		{
			// Checks the TypeScript compiler already does, so they only run on JavaScript.
			files: ['**/*.{js,jsx,mjs,cjs}'],
			rules: {
				'constructor-super': 'error',
				'getter-return': 'error',
				'no-class-assign': 'error',
				'no-const-assign': 'error',
				'no-dupe-class-members': 'error',
				'no-dupe-keys': 'error',
				'no-func-assign': 'error',
				'no-import-assign': 'error',
				'no-new-native-nonconstructor': 'error',
				'no-obj-calls': 'error',
				'no-redeclare': 'error',
				'no-setter-return': 'error',
				'no-this-before-super': 'error',
				'no-undef': 'error',
				'no-unreachable': 'error',
				'no-unsafe-negation': 'error',
				'no-with': 'error',
			},
		},
		{
			files: ['**/*.{ts,tsx,mts,cts}'],
			rules: {
				'no-var': 'error',
				'prefer-const': 'error',
				'prefer-rest-params': 'error',
				'prefer-spread': 'error',
			},
		},
		{
			files: browserFiles,
			env: {browser: true},
		},
		{
			files: nodeFiles,
			env: {node: true},
		},
		{
			files: [...browserFiles, ...nodeFiles],
			rules: {
				'license-header/header': ['error', './resources/license-header.js'],
			},
		},
		{
			files: ['packages/camunda-api-zod-schemas/lib/vite-env.d.ts'],
			rules: {
				'license-header/header': 'off',
			},
		},
		{
			files: ['packages/camunda-api-zod-schemas/lib/**/*.{js,mjs,cjs,ts,mts,cts}'],
			rules: {
				'no-unused-vars': 'off',
				'typescript/no-empty-object-type': 'off',
			},
		},
		{
			files: ['packages/{oc-saas-notifications,session-heartbeat}/lib/**/*.{ts,tsx}'],
			rules: reactHooksRules,
		},
		{
			files: [`${oc}/**/*.{js,jsx,mjs,cjs,ts,tsx,mts,cts}`],
			rules: {
				'local/api-zod-schemas-version': 'error',
			},
		},
		{
			files: [ocSrc],
			plugins: ['import'],
			rules: {
				'no-duplicate-imports': 'error',
				curly: 'error',
				'no-unused-vars': [
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
				'import/no-default-export': 'error',
				'import/group-exports': 'error',
				...reactHooksRules,
				...tanstackQueryPlugin.configs.recommended.rules,
			},
		},
		{
			files: [`${oc}/src/shared/svg/**`],
			plugins: ['import'],
			rules: {
				'import/no-default-export': 'off',
			},
		},
		{
			files: [ocSrc],
			excludeFiles: ocTestFiles,
			rules: {
				...camundaDsPlugin.configs.recommended.rules,
				'react/only-export-components': ['error', {allowConstantExport: true}],
			},
		},
		{
			files: [`${oc}/src/{admin,tasklist}/**/*.{js,jsx,ts,tsx}`, `${oc}/src/**/shadcn.components/**/*.{js,jsx,ts,tsx}`],
			excludeFiles: ocTestFiles,
			rules: camundaDsPlugin.configs.tailwind.rules,
		},
		{
			files: ocTestFiles,
			rules: {
				'vitest/expect-expect': 'error',
				'vitest/no-commented-out-tests': 'error',
				'vitest/no-disabled-tests': 'warn',
				'vitest/no-focused-tests': 'error',
				'vitest/no-identical-title': 'error',
				'vitest/no-import-node-test': 'error',
				'vitest/no-interpolation-in-snapshots': 'error',
				'vitest/no-mocks-import': 'warn',
				'vitest/no-standalone-expect': ['error', {additionalTestBlockFunctions: ['it', 'it.for']}],
				'vitest/no-unneeded-async-expect-function': 'error',
				'vitest/prefer-called-exactly-once-with': 'error',
				'vitest/require-local-test-context-for-concurrent-snapshots': 'error',
				'vitest/valid-describe-callback': 'error',
				'vitest/valid-expect': 'error',
				'vitest/valid-expect-in-promise': 'error',
				'vitest/valid-title': 'error',
				'vitest/consistent-each-for': ['error', {test: 'for', it: 'for', describe: 'each', suite: 'each'}],
				'react/globals': 'off',
			},
		},
		{
			files: [`${oc}/src/routes/**/*.{test,spec}.{js,jsx,ts,tsx}`],
			rules: {
				'local/no-tests-in-routes': 'error',
			},
		},
	],
});
