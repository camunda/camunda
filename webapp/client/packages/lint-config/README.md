# @camunda/lint-config

Shared ESLint and Prettier configuration package for Camunda frontend projects (`operate/client`, `tasklist/client`, `identity/client`).

## Package structure

```
lint-config/
├── eslint/
│   ├── index.js           # barrel re-export of all named configs
│   ├── base.js            # JS recommended + Prettier integration + universal rules
│   ├── typescript.js      # @typescript-eslint parser and rules (factory function)
│   ├── react.js           # React Hooks + react-refresh (factory function)
│   ├── testing.js         # Vitest + Testing Library (factory function)
│   ├── license.js         # license-header enforcement (factory function)
│   └── tanstack-query.js  # @tanstack/eslint-plugin-query (factory function)
└── prettier/
    └── index.js           # shared Prettier config
```

## Installation

### 1. Add `.npmrc` to the consumer project

Because this package lives in a separate workspace (`webapp/client/packages/lint-config`) from the consumer projects (`operate/client`, `tasklist/client`, `identity/client`), npm uses a **symlink** by default when installing via `file:` references. This causes Node to resolve imports from the physical path of the package rather than the consumer's `node_modules`, breaking peer dependency resolution.

To fix this, add an `.npmrc` file to the consumer project with the following setting:

```ini
install-links=true
```

This forces npm to **copy** the package into the consumer's `node_modules` instead of symlinking it, so that all peer dependencies are resolved correctly from the consumer's own `node_modules`.

### 2. Add the package to `devDependencies`

In the consumer's `package.json`:

```json
{
  "devDependencies": {
    "@camunda/lint-config": "file:../../webapp/client/packages/lint-config"
  }
}
```

### 3. Peer dependencies

This package declares all ESLint plugins as `peerDependencies`. This means:

- **Each consumer project is responsible for installing the plugins** it actually uses.
- Plugin instances are shared between the consumer and the shared config, avoiding duplicate registrations that would break ESLint flat config (e.g. `"Definition for rule X was not found"`).
- Optional plugins (e.g. `@tanstack/eslint-plugin-query`, `@vitest/eslint-plugin`) only need to be installed if the consumer uses the corresponding config factory.

The required peer dependencies across all consumers are `eslint` and `prettier`:

```json
{
  "devDependencies": {
    "eslint": "9.x",
    "prettier": "3.x"
  }
}
```

All other plugins are optional and should be installed only if the corresponding factory is used in the project's `eslint.config.js`. See the [available factories](#available-factories) section below for the full mapping.

Then run:

```sh
npm install
```

## Usage

### ESLint

Create an `eslint.config.js` in the consumer project and compose the shared factories:

```javascript
import {
  baseConfig,
  typescriptConfig,
  reactConfig,
  testingConfig,
  tanstackQueryConfig,
  licenseConfig,
} from '@camunda/lint-config/eslint';
import {defineConfig, globalIgnores} from 'eslint/config';

const files = {
  browser: ['src/**/*.{js,jsx,ts,tsx}'],
  test: ['src/**/*.test.ts', 'src/**/*.test.tsx', 'src/setupTests.ts'],
  node: ['vite.config.ts', 'playwright.config.ts'],
};

export default defineConfig([
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

  globalIgnores(['dist/*', 'node_modules/*', 'build/*', 'target/*']),
]);
```

### Prettier

Create a `prettier.config.js` in the consumer project:

```javascript
import sharedConfig from '@camunda/lint-config/prettier';

export default {
  ...sharedConfig,
  // project-specific overrides here
};
```

## Available factories

Each factory is a function that returns an array of ESLint flat config objects. They accept options so that project-specific values (file globs, tsconfig paths) are resolved in the consumer.

| Import | Required peer dependency                                                   | Options                                                                     |
|---|----------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| `baseConfig` | `eslint-config-prettier`, `eslint-plugin-prettier`, `prettier`             | —                                                                           |
| `typescriptConfig` | `@typescript-eslint/eslint-plugin`, `@typescript-eslint/parser`, `globals` | `browserFiles`, `testFiles`, `nodeFiles`, `tsconfigRootDir`, `tsProjects`, `eslint-plugin-import` |
| `reactConfig` | `eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`                 | `browserFiles`, `testFiles`                                                 |
| `testingConfig` | `@vitest/eslint-plugin`, `eslint-plugin-testing-library`                   | `testFiles`                                                                 |
| `tanstackQueryConfig` | `@tanstack/eslint-plugin-query`                                            | `browserFiles`                                                              |
| `licenseConfig` | `eslint-plugin-license-header`                                             | `licenseHeaderPath`                                                         |

> **Note:** `baseConfig` is a plain array (not a function) and can be spread directly.

### `typescriptConfig` options

| Option | Type | Description |
|---|---|---|
| `browserFiles` | `string[]` | Globs for browser source files |
| `testFiles` | `string[]` | Globs for test files |
| `nodeFiles` | `string[]` | Globs for Node.js tooling files (vite.config, playwright.config, etc.) |
| `tsconfigRootDir` | `string` | Absolute path to the directory containing the tsconfig files. Use `import.meta.dirname` in ESM or `path.dirname(fileURLToPath(import.meta.url))` in CJS |
| `tsProjects` | `string \| string[]` | Path(s) to tsconfig file(s) relative to `tsconfigRootDir`. Use multiple entries if the project has separate tsconfigs for app, vitest, and browser tests |

## Extending a config in a consumer project

Since all factories return plain arrays of ESLint flat config objects, extending or overriding any rule is straightforward — just add config objects after the shared ones:

```javascript
export default defineConfig([
  ...baseConfig,

  ...typescriptConfig({...}),

  // project-specific override: enable type-checked rules
  {
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      '@typescript-eslint/await-thenable': 'error',
      '@typescript-eslint/no-floating-promises': 'error',
    },
  },

  // project-specific override: disable a base rule for a specific directory
  {
    files: ['src/generated/**/*'],
    rules: {
      '@typescript-eslint/no-unused-vars': 'off',
    },
  },
]);
```

Rules defined after the shared factories take precedence, following standard ESLint flat config cascade order.

## Shared configurations overview

### `baseConfig`

Applied globally (no `files` filter). Includes JS recommended rules, Prettier integration, and a set of universal rules shared across all projects. Refer to [`eslint/base.js`](./eslint/base.js) for the full rule set.

### Prettier config

Defines the baseline code style options shared across all consumer projects. Refer to [`prettier/index.js`](./prettier/index.js) for the full configuration. Consumer projects can extend or override individual options as needed.
