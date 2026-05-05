# Contributing to Camunda Process Test Coverage Frontend (New)

Thank you for contributing! This document explains how to set up a local development environment, the coding conventions used in this module, and how to submit changes.

---

## Getting started

### 1. Install dependencies

```bash
npm install
```

### 2. Build

```bash
npm run build
```

This copies vendor libraries and the application sources to the Java module's `target/` directory (see `scripts/build.js` for details).

### 3. Run against real data

The easiest way to see the report in a browser is to run the Java tests in the example module first:

```bash
# From the repo root
./mvnw verify -pl testing/camunda-process-test-example -am -Dquickly -DskipTests=false -DskipITs -T1C
```

Open the generated report:

```
testing/camunda-process-test-java/target/coverage-report/index.html
```

---

## Project structure

```
camunda-process-test-coverage-frontend-new/
├── package.json          npm package descriptor
├── .npmrc                npm configuration
├── pom.xml               Maven module (runs the npm build)
├── scripts/
│   └── build.js          Node.js build script (copies vendor + sources)
├── src/
│   ├── index.html        HTML template (contains {{ COVERAGE_DATA }} placeholder)
│   ├── app.js            Single-file vanilla JS application
│   └── styles.css        Custom CSS (Camunda brand, layout)
└── public/
    └── static/
        └── media/        Static assets (logo, favicon)
```

---

## Architecture

The application is a single-page app implemented in **vanilla JavaScript** (no build step for the JS itself – no transpilation, no bundling of the application code). It follows an **IIFE** pattern to avoid polluting the global scope.

### Routing

Hash-based routing (`window.location.hash`) provides deep-linkable URLs without requiring a server:

| Hash | View |
|------|------|
| `#/` | Dashboard |
| `#/process/<processId>` | Process details |
| `#/suite/<suiteId>` | Suite details |
| `#/suite/<suiteId>/run/<runName>` | Test-case details |

The router is implemented in `parseRoute()` and `render()` in `src/app.js`.

### Sidebar

Rendered once on load, with active state updated on each route change. Expandable sections (suites and test cases) use Bootstrap 5 collapse components.

### BPMN rendering

[bpmn-js](https://bpmn.io/toolkit/bpmn-js/) `NavigatedViewer` is used. It is loaded as a pre-built UMD bundle from `vendor/bpmn-js/`. The viewer is destroyed and recreated on each navigation to a process page.

Coverage highlighting:

- **Completed elements** – CSS marker class `coverage-completed` is applied via `canvas.addMarker()`, which adds a blue stroke and light-blue fill.
- **Taken sequence flows** – coloured via the bpmn-js `graphicsFactory` API (DI stroke/fill).

---

## Coding conventions

- **Vanilla JS** – no frameworks (React, Vue, Angular, …). Keep it simple.
- **ES5 / ES2015 compatible** – the script is loaded directly in the browser without transpilation. Avoid features that require a transpiler.
- **IIFE** – wrap the entire application in `(function() { 'use strict'; … })()`.
- **No external HTTP requests** – all assets must be bundled locally; the report is self-contained.
- **HTML escaping** – always escape user-supplied strings with `escapeHtml()` before inserting into the DOM via `innerHTML`.
- **Camunda brand colours** – use the colour constants defined at the top of `app.js`.

### CSS conventions

- Follow [BEM](https://getbem.com/)-ish naming for custom classes (e.g., `.coverage-badge`, `.coverage-bar-wrap`).
- Camunda brand colours are defined as comments at the top of `styles.css`.
- Avoid inline styles in HTML; prefer CSS classes.

---

## Updating vendor libraries

```bash
# Check for updates
npm outdated

# Update to latest (check for breaking changes first!)
npm install bpmn-js@latest bootstrap@latest bootstrap-icons@latest

# Rebuild to copy the new vendor files
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
