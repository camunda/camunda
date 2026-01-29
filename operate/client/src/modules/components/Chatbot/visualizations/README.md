# Chatbot Visualizations

This module provides automatic data visualization for MCP tool responses in the chatbot.

## Features

- **Automatic Detection**: Analyzes tool results and automatically generates appropriate visualizations
- **Timeline Charts**: Shows process instances and incidents created over time
- **Compact Display**: Optimized for the chatbot's 400px width with clear, readable charts
- **Carbon Design Integration**: Uses `@carbon/charts` for consistent styling with the Operate UI

## Supported Visualizations

### Timeline Charts

Automatically generated for:
- **Process Instance Search** (`search_process_instances`): Groups instances by `startDate`
- **Incident Search** (`search_incidents`): Groups incidents by `creationTime`

Charts aggregate data by day and display counts over time.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    useChat Hook                          │
│                                                           │
│  1. Execute MCP Tool                                     │
│     ↓                                                     │
│  2. analyzeToolResponse(toolName, result)                │
│     ↓                                                     │
│  3. Generate VisualizationData                           │
│     ↓                                                     │
│  4. Attach to Message                                    │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                  Chatbot Component                       │
│                                                           │
│  MessageBubble                                           │
│  ├── message.content (text)                             │
│  └── ChartRenderer (if visualization exists)            │
└─────────────────────────────────────────────────────────┘
```

## Files

- **`types.ts`**: Type definitions for visualization data
- **`dataTransform.ts`**: Analyzes tool responses and transforms data into chart format
- **`ChartRenderer.tsx`**: Renders charts using Carbon Charts
- **`styled.tsx`**: Styled components for chart containers

## How It Works

### 1. Tool Execution

When the LLM calls an MCP tool (e.g., `search_process_instances`), the result is captured:

```typescript
const toolCall = {
  name: 'search_process_instances',
  result: {
    items: [
      { processInstanceKey: '123', startDate: '2026-01-28T10:00:00Z' },
      { processInstanceKey: '124', startDate: '2026-01-28T11:00:00Z' },
      // ...
    ]
  }
};
```

### 2. Analysis

The `analyzeToolResponse` function:
1. Identifies the tool name
2. Extracts array data from the result
3. Checks for time-series fields (`startDate`, `creationTime`)
4. Groups data by day and counts occurrences

### 3. Visualization Generation

If suitable data is found, creates a `VisualizationData` object:

```typescript
{
  type: 'timeline',
  title: 'Process Instances Created Over Time',
  data: [
    { group: 'Process Instances', date: Date, value: 5 },
    { group: 'Process Instances', date: Date, value: 8 },
    // ...
  ],
  options: { /* Carbon Charts config */ }
}
```

### 4. Rendering

The `ChartRenderer` component displays the chart using `@carbon/charts-react`:

```tsx
<ChartRenderer visualization={message.visualization} />
```

## Extending Visualizations

### Adding New Chart Types

1. **Update Types** (`types.ts`):
   ```typescript
   export type VisualizationType = 'timeline' | 'bar' | 'pie' | 'none';
   ```

2. **Add Analysis Logic** (`dataTransform.ts`):
   ```typescript
   export function analyzeToolResponse(toolName: string, result: unknown) {
     if (toolName === 'my_new_tool') {
       return analyzeMyNewTool(result);
     }
     // ...
   }
   ```

3. **Update Renderer** (`ChartRenderer.tsx`):
   ```tsx
   if (visualization.type === 'bar') {
     return <BarChart data={visualization.data} options={visualization.options} />;
   }
   ```

### Adding New Tool Support

To add visualization for a new MCP tool:

1. Identify the data structure in the tool's response
2. Add a new analysis function in `dataTransform.ts`
3. Add a case in `analyzeToolResponse` for the tool name

Example:

```typescript
function analyzeMyTool(result: unknown): VisualizationData | null {
  const data = extractArrayFromResult(result);
  if (!data || data.length === 0) return null;

  // Transform data
  const chartData = data.map(item => ({
    group: 'My Data',
    value: item.count,
  }));

  return {
    type: 'bar',
    title: 'My Tool Results',
    data: chartData,
    options: { /* config */ }
  };
}
```

## Chart Configuration

Charts are configured to fit the chatbot's compact space:

- **Height**: 250px (configurable in `options.height`)
- **Width**: Responsive (fills message bubble)
- **Theme**: 'g100' (dark theme from Carbon)
- **Points**: Enabled for timeline charts (radius: 4px)
- **Axes**: Compact font sizes (10-11px)

## Testing

To test visualizations:

1. Ask the chatbot: "Show me process instances created today"
2. The LLM will call `search_process_instances`
3. If results contain `startDate` fields, a timeline chart will appear
4. Check browser console for: `[useChat] Generated visualization: timeline for tool: search_process_instances`

## Future Enhancements

- **Bar Charts**: For categorical comparisons (e.g., incidents by error type)
- **Pie/Donut Charts**: For proportions (e.g., process states)
- **Data Tables**: For structured data with inline visualizations
- **Expand/Collapse**: Allow users to expand charts to full screen
- **Export**: Download chart data as CSV
- **Customization**: User preferences for chart types
