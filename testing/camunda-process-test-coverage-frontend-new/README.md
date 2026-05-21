# Camunda Process Test Coverage Frontend (New)

A lightweight frontend for visualising [Camunda Process Test](../camunda-process-test-java/README.md) coverage reports.

This module will replace `camunda-process-test-coverage-frontend`. It uses **camunda-bpmn-js** (Camunda-branded BPMN viewer), **Bootstrap 5**, and **Webpack 5** for bundling.

The build produces a **JAR** consumed by `camunda-process-test-java` as a Maven compile dependency. The JAR contains `coverage/index.html` and `coverage/static/…` which `CoverageReportUtil` reads from the classpath to generate self-contained HTML reports.

---

## Building

```bash
# Install dependencies
npm install

# Production build  (outputs to target/generated-frontend-resources/coverage/)
npm run build

# Development build with watch mode
npm run dev
```

The production build bundles all vendor libraries and application modules into:

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

To preview with real data, see [CONTRIBUTING.md](CONTRIBUTING.md).
