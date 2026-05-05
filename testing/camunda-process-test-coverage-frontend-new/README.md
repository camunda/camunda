# Camunda Process Test Coverage Frontend (New)

A lightweight, vanilla-JavaScript frontend for visualising [Camunda Process Test](../camunda-process-test-java/README.md) coverage reports.

This module eventually replaces `camunda-process-test-coverage-frontend`. It avoids heavy frameworks (React, Angular, Vue) and instead uses:

- **[bpmn-js](https://bpmn.io/toolkit/bpmn-js/)** – BPMN diagram rendering
- **[Bootstrap 5](https://getbootstrap.com/)** – responsive layout and components
- **[Bootstrap Icons](https://icons.getbootstrap.com/)** – icon set

The build produces static HTML/CSS/JS files that are bundled inside the `camunda-process-test-java` JAR and injected with coverage data at runtime.

---

## Features

- **Dashboard** – summary statistics (test suites, test cases, processes, average coverage)
- **Process view** – BPMN diagram with completed elements and taken sequence flows highlighted in Camunda Blue; coverage breakdown table
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

# 2. Build static files into the Java module's target directory
npm run build

# 3. Open the generated file in a browser
open ../camunda-process-test-java/target/generated-frontend-resources/coverage/index.html
```

The `build` script copies all vendor files (bpmn-js, Bootstrap, Bootstrap Icons) and the application sources into the output directory. There is **no watch/hot-reload** mode because the sources are plain JS and CSS – edit `src/app.js` or `src/styles.css` and re-run `npm run build`.

### Working with sample data

The generated `index.html` expects `window.COVERAGE_DATA` to be set. When opened directly from the filesystem (without the Java module injecting real data), you will see an error message. To develop against sample data:

1. Run the Java tests once to generate a real report (or use the example module).
2. Open the generated `coverage-report/index.html` that the Java plugin writes to `target/`.

---

## Build output

```
coverage/
├── index.html                  ← HTML template (window.COVERAGE_DATA placeholder)
└── static/
    ├── app.js                  ← application (vanilla JS)
    ├── styles.css              ← custom styles (Camunda brand)
    ├── media/
    │   ├── camunda-logo.png
    │   └── favicon.ico
    └── vendor/
        ├── bpmn-js/            ← bpmn-js viewer bundle + CSS/fonts
        ├── bootstrap/          ← Bootstrap CSS + JS bundle
        └── bootstrap-icons/    ← Bootstrap Icons CSS + fonts
```

---

## Maven integration

The Maven build in `camunda-process-test-java/pom.xml` uses `frontend-maven-plugin` to:

1. Install Node + npm (via `frontend-maven-plugin`).
2. Run `npm install` in this module.
3. Run `npm run build` (sets `BUILD_PATH` to the Java module's `target/generated-frontend-resources/coverage`).

The Java module then includes that target directory as an unfiltered resource directory, so all files are bundled into the JAR. `CoverageReportUtil.toHtml()` reads `coverage/index.html` from the classpath and replaces `{{ COVERAGE_DATA }}` with the serialised `HtmlCoverageReport` JSON.

---

## Data format

The page reads `window.COVERAGE_DATA`, which is a serialised `HtmlCoverageReport` object:

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

All runtime libraries are plain npm dependencies. Update them with:

```bash
npm install bpmn-js@latest bootstrap@latest bootstrap-icons@latest
npm run build
```

Then rebuild the Java module to pick up the new vendor files.
