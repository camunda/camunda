# Contributing to Camunda Process Test Coverage Frontend (New)

Thank you for contributing! This document explains how to set up a local development environment, the coding conventions used in this module, and how to submit changes.

---

## Getting started

### 1. Install dependencies

```bash
npm install
```

### 2. Build (production)

```bash
npm run build
```

This runs Webpack in production mode, bundling all dependencies and outputting to `target/generated-frontend-resources/coverage/`.

### 3. Build (development / watch)

```bash
npm run dev
```

Runs Webpack in development mode with `--watch`. Re-bundles on any source file change.

### 4. Run against real data

The easiest way to see the report in a browser is to run the Java tests in the example module first:

```bash
# From the repo root
./mvnw verify -pl testing/camunda-process-test-example -am -Dquickly -DskipTests=false -DskipITs -T1C
```

Open the generated report:

```
testing/camunda-process-test-example/target/coverage-report/index.html
```

---

## Project structure

```
camunda-process-test-coverage-frontend-new/
├── package.json          npm package descriptor
├── webpack.config.js     Webpack configuration
├── .npmrc                npm configuration
├── pom.xml               Maven module (packaging=jar)
├── src/
│   ├── app.js            Entry point
│   ├── utils.js          Shared helpers (formatting, HTML escaping)
│   ├── router.js         Hash-based router
│   ├── sidebar.js        Sidebar navigation
│   ├── bpmn.js           camunda-bpmn-js viewer wrapper
│   ├── styles.css        Custom styles
│   ├── index.html        HTML template
│   └── views/
│       ├── dashboard.js
│       ├── process.js
│       ├── suite.js
│       └── run.js
└── public/
    └── static/media/     Static assets (logo, favicon)
```

---

## Architecture

### Webpack bundling

Webpack (configured in `webpack.config.js`) handles:

- **Entry**: `src/app.js` imports all ES modules and CSS.
- **CSS**: `MiniCssExtractPlugin` extracts all CSS (Bootstrap, Bootstrap Icons, camunda-bpmn-js, custom) into `bundle.css`. Fonts referenced by CSS are copied to `static/fonts/` by webpack's `asset/resource` rule.
- **Output**: `bundle.js` (all JS) + `bundle.css` (all CSS) in `coverage/static/`.
- **HTML**: `CopyWebpackPlugin` copies `src/index.html` to `coverage/index.html`.
- **Media**: Logo and favicon are copied to `coverage/static/media/`.

The `BUILD_PATH` environment variable (set by Maven) tells webpack where to write the `coverage/` output directory.

### Routing

Hash-based routing (`window.location.hash`) provides deep-linkable URLs:

| Hash | View |
|------|------|
| `#/` | Dashboard |
| `#/process/<processId>` | Process details |
| `#/suite/<suiteId>` | Suite details |
| `#/suite/<suiteId>/run/<runName>` | Test-case details |

The router is implemented in `src/router.js`.

### BPMN rendering

`camunda-bpmn-js` `NavigatedViewer` is used. It is destroyed and recreated on each navigation to a process page (see `src/bpmn.js`).

Coverage highlighting:
- **Completed elements** – CSS marker class `coverage-completed` via `canvas.addMarker()`.
- **Taken sequence flows** – blue stroke via `graphicsFactory.update()`.

---

## Coding conventions

- **ES modules** – use `import`/`export` throughout. Webpack handles bundling.
- **No class components** – keep code functional and simple.
- **HTML escaping** – always escape user-supplied strings with `escapeHtml()` from `utils.js`.
- **Camunda brand colours** – use the `COLORS` constants from `utils.js`.

### CSS conventions

- Camunda brand colours are defined as comments at the top of `styles.css`.
- Avoid inline styles in HTML; prefer CSS classes.
- Custom classes use a `coverage-` prefix.

---

## Updating vendor libraries

```bash
# Check for updates
npm outdated

# Update to latest (check for breaking changes first!)
npm install camunda-bpmn-js@latest bootstrap@latest bootstrap-icons@latest

# Rebuild
npm run build
```

---

## Pull request checklist

Before opening a PR that touches this module:

- [ ] `npm run build` completes without errors.
- [ ] The report renders correctly in Chrome and Firefox.
- [ ] BPMN diagrams display and coverage highlighting works.
- [ ] Deep-links (`#/process/…`, `#/suite/…`) work.
- [ ] No changes to `node_modules/` or the `node/` directory (these are gitignored).
- [ ] Java formatting applied: `./mvnw license:format spotless:apply -T1C` (for `pom.xml` changes).
