# Power BI BPMN Viewer

A custom Power BI visual that renders BPMN 2.0 diagrams using the bpmn.io library with execution heatmap support.

## Features

- Renders BPMN 2.0 XML diagrams
- Execution heatmap visualization showing flow node activity
- Automatically fits diagrams to viewport
- Built with bpmn-js viewer library
- Displays default sample diagram when no data is provided
- Color-coded heat gradient (white → yellow → orange → red)

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

### Basic BPMN Rendering

1. Import the `.pbiviz` file into Power BI Desktop
2. Add the BPMN Viewer visual to your report
3. Bind a field containing BPMN 2.0 XML to the "BPMN XML" data role
4. The visual will render the BPMN diagram

### Heatmap Visualization

To display execution heatmap on the diagram:

1. Bind your flow node ID field to the "Flow Node ID" data role
2. Bind your execution count field to the "Execution Count" data role
3. The visual will automatically color-code the diagram elements based on execution frequency

**Example Data Structure:**
```
Flow Node ID  | Execution Count
--------------+-----------------
StartEvent_1  | 1000
Task_1        | 950
Task_2        | 800
EndEvent_1    | 750
```

The visual will:
- Color each element based on its execution count (higher counts = warmer colors)
- Display the count as an overlay badge on each element
- Use a gradient: white (lowest) → yellow → orange → red (highest)

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
