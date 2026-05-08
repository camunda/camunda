# Contributing to Camunda Process Test Coverage

This guide covers everything you need to contribute to this coverage module.

---

## Prerequisites

|     Tool     |                       Version                       |
|--------------|-----------------------------------------------------|
| Node.js      | ≥ 20 (CI uses v24.13.0 via `frontend-maven-plugin`) |
| npm          | ≥ 10 (CI uses 11.9.0)                               |
| Java / Maven | 21 / 3.x (for Maven integration)                    |

---

## Getting started

```bash
# Install dependencies
npm install

# Production build
npm run build

# Development build with watch mode
npm run dev
```

### Previewing with real data

1. Run the example module tests to generate a coverage report:

   ```bash
   ./mvnw verify -pl testing/camunda-process-test-example -am -Dquickly -DskipTests=false -DskipITs -T1C
   ```
2. Open the generated HTML report:

   ```
   testing/camunda-process-test-example/target/coverage-report/index.html
   ```

---

## Project structure

```
camunda-process-test-coverage/
├── package.json          npm package descriptor
├── webpack.config.js     Webpack configuration (bundling + CSS extraction)
├── pom.xml               Maven module (packaging=jar, runs webpack via frontend-maven-plugin)
├── src/
│   ├── app.js            Entry point – imports CSS, wires router + views
│   ├── utils.js          Shared helpers (formatting, HTML escaping, colours)
│   ├── router.js         Hash-based router
│   ├── sidebar.js        Sidebar navigation component
│   ├── bpmn.js           camunda-bpmn-js viewer wrapper + zoom controls
│   ├── styles.css        Custom styles (Camunda brand, layout)
│   ├── index.html        HTML template ({{ COVERAGE_DATA }} placeholder)
│   └── views/
│       ├── dashboard.js  Dashboard view
│       ├── process.js    Process details view (BPMN diagram)
│       ├── suite.js      Test suite view
│       └── run.js        Test case (run) view
└── public/
    └── static/media/     Logo, favicon
```

---

## Architecture

### Webpack bundling

Webpack (`webpack.config.js`) handles:

- **Entry**: `src/app.js` imports all ES modules and CSS.
- **CSS**: `MiniCssExtractPlugin` extracts all CSS into `bundle.css`. Fonts are copied to `static/fonts/` via the `asset/resource` rule.
- **HTML / media**: `CopyWebpackPlugin` copies `src/index.html` and `public/static/media/` to the output directory.
- **Output path**: The `BUILD_PATH` environment variable (set by Maven) controls where webpack writes the `coverage/` directory.

### Routing

Hash-based routing (`window.location.hash`) provides deep-linkable URLs:

|                         Hash                          |                    View                     |
|-------------------------------------------------------|---------------------------------------------|
| `#/`                                                  | Dashboard                                   |
| `#/process/<processId>`                               | Process details (global aggregate coverage) |
| `#/suite/<suiteId>`                                   | Suite details                               |
| `#/suite/<suiteId>/run/<runName>`                     | Test-case details                           |
| `#/suite/<suiteId>/run/<runName>/process/<processId>` | Process view scoped to a test run           |

### BPMN rendering

`camunda-bpmn-js` `NavigatedViewer` is used. It is destroyed and re-created on each navigation to a process page (`src/bpmn.js`).

Coverage highlighting uses `canvas.addMarker()`:
- **Completed elements** → marker `coverage-completed` (blue fill via CSS)
- **Taken sequence flows** → marker `coverage-taken` (blue stroke via CSS)

Zoom controls are exposed as `window.bpmnZoomIn/bpmnZoomOut/bpmnZoomReset` and wired to toolbar buttons rendered in `process.js`.

---

## Coding conventions

- **ES modules** – use `import`/`export` throughout.
- **HTML escaping** – always escape user-supplied strings with `escapeHtml()` from `utils.js`.
- **No inline styles** – use CSS classes from `styles.css`.
- **Camunda brand colours** – use `COLORS` constants from `utils.js`.

---

## Updating vendor dependencies

```bash
# Check for updates
npm outdated

# Update a specific package
npm install camunda-bpmn-js@latest

# Rebuild
npm run build
```
