# Camunda Process Test Coverage Frontend

An application that provides an interactive visualization and reporting interface for Camunda process test coverage.
This tool helps developers understand test coverage across BPMN processes, test suites, and individual test runs.

## Features

- Interactive coverage reports with detailed statistics
- Process-level and test suite-level coverage visualization
- Coverage metrics and trends analysis
- User-friendly interface built with React and Carbon Design System

### Prerequisites

- Node.js v22.9.0 (as specified in the Maven plugin configuration)
- npm 10.8.3 or compatible version

### Local Development

```bash
# Install dependencies
npm install

# Start development server
npm start
```

The development server will start on http://localhost:3000 with hot reloading enabled.

### Available Scripts

- `npm start` - Start development server
- `npm build` - Build for production (outputs to ../camunda-process-test-java/src/main/resources/coverage/ directory)
- `npm test` - Run tests
- `npm run lint` - Run linting

## Integration & Build Process

This frontend is automatically integrated into the Camunda Process Test Coverage Java plugin using the `frontend-maven-plugin`.
The integration works as follows:

### Maven Integration

The frontend is built automatically during the Maven build process via the frontend-maven-plugin in `../camunda-process-test-java/pom.xml`:

1. Node/NPM Installation - Installs Node.js v22.9.0 and npm 10.8.3 during the initialize phase
2. Dependency Installation - Runs npm install to install frontend dependencies
3. Frontend Build - Executes npm run build during the generate-resources phase
4. Resource Packaging - The built frontend assets are automatically copied to src/main/resources/coverage/ and packaged into the Java plugin JAR

### Usage

To use the coverage reporting with HTML frontend:

1. The frontend is automatically bundled with the Java plugin - no separate installation required
2. Run your Camunda process tests
3. The plugin generates HTML reports with this frontend embedded
4. Open the generated `index.html` file in your browser to view interactive coverage reports

### Build Output

When built via Maven, the frontend assets are placed in the Java plugin's resources at coverage/static/ and serve as the embedded HTML reporting interface.

