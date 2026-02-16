# Power BI BPMN Viewer

A custom Power BI visual that renders BPMN 2.0 diagrams using the bpmn.io library.

## Features

- Renders BPMN 2.0 XML diagrams
- Automatically fits diagrams to viewport
- Built with bpmn-js viewer library
- Displays default sample diagram when no data is provided

## Installation

1. Install dependencies:
```bash
cd bpmnViewer
npm install
```

2. Start development server:
```bash
npm start
```

3. Build the package:
```bash
npm run package
```

This will create a `.pbiviz` file in the `dist/` directory that can be imported into Power BI Desktop.

## Usage

1. Import the `.pbiviz` file into Power BI Desktop
2. Add the BPMN Viewer visual to your report
3. Bind a field containing BPMN 2.0 XML to the "BPMN XML" data role
4. The visual will render the BPMN diagram

## Development

- **Start dev server**: `npm start`
- **Build package**: `npm run package`
- **Run linter**: `npm run lint`

## Dependencies

- bpmn-js: ^18.12.0 - BPMN rendering library
- Power BI Visuals API: 5.4.0
- TypeScript: 5.3.3

## License

MIT
