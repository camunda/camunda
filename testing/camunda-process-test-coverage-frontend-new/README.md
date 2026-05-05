# Camunda Process Test Coverage Frontend (New)

A lightweight frontend for visualising [Camunda Process Test](../camunda-process-test-java/README.md) coverage reports.

This module eventually replaces `camunda-process-test-coverage-frontend`. It uses:

- **[camunda-bpmn-js](https://github.com/camunda/camunda-bpmn-js)** – Camunda-branded BPMN diagram rendering (NavigatedViewer)
- **[Bootstrap 5](https://getbootstrap.com/)** – responsive layout and components
- **[Bootstrap Icons](https://icons.getbootstrap.com/)** – icon set
- **[Webpack 5](https://webpack.js.org/)** – bundles all dependencies into `bundle.js` + `bundle.css`

The build produces a **JAR** that is consumed by `camunda-process-test-java` as a regular Maven dependency. The JAR contains `coverage/index.html` and `coverage/static/…` which `CoverageReportUtil` reads from the classpath to generate self-contained HTML reports.

---

## Features

- **Dashboard** – summary statistics (test suites, test cases, processes, average coverage)
- **Process view** – bpmn-js diagram with completed elements and taken sequence flows highlighted in Camunda Blue; coverage breakdown table
- **Suite view** – per-suite coverage over all processes and list of test cases
- **Test-case view** – list of processes covered by a single test method
- **Sidebar navigation** – hierarchical tree (Processes / Test Suites / Test Cases / Processes)
- **Deep-links** – hash-based routing; every page is bookmarkable:
  - `#/` – Dashboard
  - `#/process/<processId>` – Process details
  - `#/suite/<suiteId>` – Suite details
  - `#/suite/<suiteId>/run/<runName>` – Test-case details

---

## Prerequisites

| Tool | Version |
|------|---------|
| Node.js | ≥ 20 (CI uses v24.13.0 via `frontend-maven-plugin`) |
| npm | ≥ 10 (CI uses 11.9.0) |
| Java / Maven | 21 / 3.x (for Maven integration) |

---

## Local development

```bash
# 1. Install dependencies
npm install

# 2. Production build (outputs to target/generated-frontend-resources/coverage/)
npm run build

# 3. Development build with watch mode
npm run dev
```

The webpack build (`npm run build`) bundles all vendor libraries and application modules into:

```
target/generated-frontend-resources/
└── coverage/
    ├── index.html            ← copied from src/index.html
    └── static/
        ├── bundle.js         ← all JS (camunda-bpmn-js, Bootstrap, application)
        ├── bundle.css        ← all CSS (extracted by MiniCssExtractPlugin)
        ├── fonts/            ← font files referenced by CSS
        └── media/            ← logo, favicon
```

### Previewing the report

To preview with sample data:

1. Run the Java tests to generate a real report:
   ```bash
   ./mvnw verify -pl testing/camunda-process-test-example -am -DskipTests=false -DskipITs -Dquickly -T1C
   ```
2. Open the generated coverage report in a browser:
   ```
   testing/camunda-process-test-example/target/coverage-report/index.html
   ```

---

## Project structure

```
camunda-process-test-coverage-frontend-new/
├── package.json          npm package descriptor
├── webpack.config.js     Webpack configuration (bundling + CSS extraction)
├── .npmrc                npm configuration
├── pom.xml               Maven module (packaging=jar, runs webpack via frontend-maven-plugin)
├── src/
│   ├── app.js            Entry point – imports CSS, wires router + views
│   ├── utils.js          Shared helper functions
│   ├── router.js         Hash-based router
│   ├── sidebar.js        Sidebar navigation component
│   ├── bpmn.js           camunda-bpmn-js viewer wrapper
│   ├── styles.css        Custom styles (Camunda brand, layout)
│   ├── index.html        HTML template ({{ COVERAGE_DATA }} placeholder)
│   └── views/
│       ├── dashboard.js  Dashboard view
│       ├── process.js    Process details view (BPMN diagram)
│       ├── suite.js      Test suite view
│       └── run.js        Test case (run) view
└── public/
    └── static/
        └── media/        Logo, favicon
```

---

## Maven integration

The module uses `packaging=jar`. The Maven lifecycle:

1. `frontend-maven-plugin` installs Node + npm and runs `npm install` (phase `initialize`).
2. `frontend-maven-plugin` runs `npm run build` (phase `generate-resources`), passing `BUILD_PATH` to Webpack.
3. Maven resource processing packages `target/generated-frontend-resources/` into the JAR.
4. `camunda-process-test-java` declares this artifact as a compile-scope dependency; the frontend resources are available on the classpath.
5. `CoverageReportUtil.toHtml()` reads `coverage/index.html` from the classpath and replaces `{{ COVERAGE_DATA }}` with the serialised `HtmlCoverageReport` JSON.

---

## Data format

The page reads `window.COVERAGE_DATA`, set by `CoverageReportUtil`:

```jsonc
{
  "suites": [
    {
      "id": "io.example.MySuiteTest",
      "name": "MySuiteTest",
      "runs": [
        {
          "name": "shouldDoSomething",
          "coverages": [
            {
              "processDefinitionId": "order-process",
              "completedElements": ["StartEvent_1", "Task_1"],
              "takenSequenceFlows": ["Flow_1"],
              "coverage": 0.75
            }
          ]
        }
      ],
      "models": [
        { "processDefinitionId": "order-process", "totalElementCount": 8, "version": "1" }
      ],
      "coverages": [ /* aggregated for this suite */ ]
    }
  ],
  "coverages": [ /* aggregated across all suites */ ],
  "definitions": {
    "order-process": "<?xml version=\"1.0\"…"
  }
}
```

---

## Dependency updates

```bash
# Check for updates
npm outdated

# Update a package
npm install camunda-bpmn-js@latest

# Rebuild
npm run build
```
