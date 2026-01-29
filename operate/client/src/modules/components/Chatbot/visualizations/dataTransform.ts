/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {parseISO, startOfDay, format} from 'date-fns';
import {ScaleTypes} from '@carbon/charts';
import type {
  VisualizationData,
  ProcessInstanceItem,
  IncidentItem,
  TimeSeriesDataPoint,
} from './types';

/**
 * Analyzes MCP tool response and determines if visualization is possible
 */
export function analyzeToolResponse(
  toolName: string,
  result: unknown
): VisualizationData | null {
  console.log('[analyzeToolResponse] Analyzing tool:', toolName, 'result type:', typeof result);
  
  if (!result || typeof result !== 'object') {
    console.log('[analyzeToolResponse] Result is not an object, skipping');
    return null;
  }

  // Log the result structure for debugging
  console.log('[analyzeToolResponse] Result structure:', JSON.stringify(result, null, 2).substring(0, 500));

  // Handle process instance search results
  if (toolName === 'searchProcessInstances' || toolName === 'search_process_instances') {
    console.log('[analyzeToolResponse] Processing process instances');
    return analyzeProcessInstances(result);
  }

  // Handle incident search results
  if (toolName === 'searchIncidents' || toolName === 'search_incidents') {
    console.log('[analyzeToolResponse] Processing incidents');
    return analyzeIncidents(result);
  }

  console.log('[analyzeToolResponse] Tool name not recognized:', toolName);
  return null;
}

/**
 * Analyzes multiple tool results for comparative visualization
 */
export function analyzeComparativeResults(
  results: Array<{toolName: string; result: unknown; label?: string}>
): VisualizationData | null {
  console.log('[analyzeComparativeResults] Analyzing', results.length, 'results for comparison');
  
  if (results.length < 2) {
    console.log('[analyzeComparativeResults] Need at least 2 results for comparison');
    return null;
  }

  // Check if all results are from the same tool type
  const toolTypes = results.map(r => {
    if (r.toolName === 'searchProcessInstances' || r.toolName === 'search_process_instances') {
      return 'processInstances';
    }
    if (r.toolName === 'searchIncidents' || r.toolName === 'search_incidents') {
      return 'incidents';
    }
    return 'unknown';
  });

  const uniqueTypes = new Set(toolTypes);
  
  if (uniqueTypes.size === 1) {
    // Same tool type - compare within same entity
    const type = Array.from(uniqueTypes)[0];
    if (type === 'processInstances') {
      return compareProcessInstances(results);
    } else if (type === 'incidents') {
      return compareIncidents(results);
    }
  } else if (uniqueTypes.size === 2 && uniqueTypes.has('processInstances') && uniqueTypes.has('incidents')) {
    // Different types - compare process instances vs incidents
    return compareProcessInstancesVsIncidents(results);
  }

  return null;
}

/**
 * Analyzes process instance data and creates timeline visualization
 */
function analyzeProcessInstances(result: unknown): VisualizationData | null {
  const data = extractArrayFromResult(result);
  if (!data || data.length === 0) {
    return null;
  }

  // Extract items with startDate (or similar timestamp fields)
  const items = data.filter(
    (item): item is ProcessInstanceItem =>
      typeof item === 'object' && item !== null && 
      ('startDate' in item || 'creationTime' in item || 'timestamp' in item || 'created' in item)
  );

  if (items.length === 0) {
    return null;
  }

  // Determine time granularity based on data spread
  const timestamps = items
    .map(item => item.startDate || (item as any).creationTime || (item as any).timestamp || (item as any).created)
    .filter(Boolean)
    .map(ts => parseISO(ts as string).getTime());

  if (timestamps.length === 0) return null;

  const timeSpanMs = Math.max(...timestamps) - Math.min(...timestamps);
  const useHourGranularity = timeSpanMs < 24 * 60 * 60 * 1000; // Less than 24 hours
  const useMinuteGranularity = timeSpanMs < 60 * 60 * 1000; // Less than 1 hour

  // Group by appropriate time unit
  const timeCounts = new Map<string, number>();

  items.forEach((item) => {
    const timestamp = item.startDate || (item as any).creationTime || (item as any).timestamp || (item as any).created;
    
    if (timestamp && typeof timestamp === 'string') {
      try {
        const date = parseISO(timestamp);
        let timeKey: string;
        
        if (useMinuteGranularity) {
          // Group by minute
          timeKey = format(date, 'yyyy-MM-dd HH:mm');
        } else if (useHourGranularity) {
          // Group by hour
          timeKey = format(date, 'yyyy-MM-dd HH:00');
        } else {
          // Group by day
          timeKey = format(startOfDay(date), 'yyyy-MM-dd');
        }
        
        timeCounts.set(timeKey, (timeCounts.get(timeKey) || 0) + 1);
      } catch {
        // Skip invalid dates
      }
    }
  });

  if (timeCounts.size === 0) {
    return null;
  }

  // Convert to chart data
  const chartData: TimeSeriesDataPoint[] = Array.from(timeCounts.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([timeStr, count]) => ({
      group: 'Process Instances',
      date: parseISO(timeStr),
      value: count,
    }));

  const granularityLabel = useMinuteGranularity ? 'by Minute' : useHourGranularity ? 'by Hour' : 'by Day';

  return {
    type: 'timeline',
    title: `Process Instances Created Over Time (${granularityLabel})`,
    data: chartData,
    options: {
      axes: {
        bottom: {
          title: 'Time',
          mapsTo: 'date',
          scaleType: ScaleTypes.TIME,
        },
        left: {
          title: 'Number of Instances',
          mapsTo: 'value',
          scaleType: ScaleTypes.LINEAR,
        },
      },
      height: '250px',
      width: '100%',
      resizable: true,
      curve: 'curveMonotoneX',
      points: {
        enabled: true,
        radius: 4,
      },
      timeScale: {
        addSpaceOnEdges: 1,
      },
    },
  };
}

/**
 * Analyzes incident data and creates timeline visualization
 */
function analyzeIncidents(result: unknown): VisualizationData | null {
  const data = extractArrayFromResult(result);
  console.log('[analyzeIncidents] Extracted array length:', data?.length || 0);
  
  if (!data || data.length === 0) {
    console.log('[analyzeIncidents] No data array found');
    return null;
  }

  // Log first item structure
  console.log('[analyzeIncidents] First item:', JSON.stringify(data[0]));
  console.log('[analyzeIncidents] First item keys:', Object.keys(data[0] as Record<string, unknown>));

  // Extract items with creationTime (or similar timestamp fields)
  const items = data.filter(
    (item): item is IncidentItem =>
      typeof item === 'object' && item !== null && 
      ('creationTime' in item || 'creationDate' in item || 'timestamp' in item || 'created' in item)
  );

  console.log('[analyzeIncidents] Items with timestamp fields:', items.length);

  if (items.length === 0) {
    console.log('[analyzeIncidents] No items with creationTime field found');
    return null;
  }

  // Determine time granularity based on data spread
  const timestamps = items
    .map(item => item.creationTime || (item as any).creationDate || (item as any).timestamp || (item as any).created)
    .filter(Boolean)
    .map(ts => parseISO(ts as string).getTime());

  if (timestamps.length === 0) return null;

  const timeSpanMs = Math.max(...timestamps) - Math.min(...timestamps);
  const useHourGranularity = timeSpanMs < 24 * 60 * 60 * 1000; // Less than 24 hours
  const useMinuteGranularity = timeSpanMs < 60 * 60 * 1000; // Less than 1 hour

  // Group by appropriate time unit
  const timeCounts = new Map<string, number>();

  items.forEach((item) => {
    const timestamp = item.creationTime || (item as any).creationDate || (item as any).timestamp || (item as any).created;
    
    if (timestamp && typeof timestamp === 'string') {
      try {
        const date = parseISO(timestamp);
        let timeKey: string;
        
        if (useMinuteGranularity) {
          // Group by minute
          timeKey = format(date, 'yyyy-MM-dd HH:mm');
        } else if (useHourGranularity) {
          // Group by hour
          timeKey = format(date, 'yyyy-MM-dd HH:00');
        } else {
          // Group by day
          timeKey = format(startOfDay(date), 'yyyy-MM-dd');
        }
        
        timeCounts.set(timeKey, (timeCounts.get(timeKey) || 0) + 1);
      } catch (error) {
        console.log('[analyzeIncidents] Failed to parse date:', timestamp, error);
      }
    }
  });

  if (timeCounts.size === 0) {
    return null;
  }

  // Convert to chart data
  const chartData: TimeSeriesDataPoint[] = Array.from(timeCounts.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([timeStr, count]) => ({
      group: 'Incidents',
      date: parseISO(timeStr),
      value: count,
    }));

  const granularityLabel = useMinuteGranularity ? 'by Minute' : useHourGranularity ? 'by Hour' : 'by Day';

  return {
    type: 'timeline',
    title: `Incidents Created Over Time (${granularityLabel})`,
    data: chartData,
    options: {
      axes: {
        bottom: {
          title: 'Time',
          mapsTo: 'date',
          scaleType: ScaleTypes.TIME,
        },
        left: {
          title: 'Number of Incidents',
          mapsTo: 'value',
          scaleType: ScaleTypes.LINEAR,
        },
      },
      height: '250px',
      width: '100%',
      resizable: true,
      curve: 'curveMonotoneX',
      points: {
        enabled: true,
        radius: 4,
      },
      timeScale: {
        addSpaceOnEdges: 1,
      },
    },
  };
}

/**
 * Extracts array data from various result structures
 */
function extractArrayFromResult(result: unknown): unknown[] | null {
  console.log('[extractArrayFromResult] Input type:', typeof result, 'is array:', Array.isArray(result));
  
  if (Array.isArray(result)) {
    console.log('[extractArrayFromResult] Direct array found, length:', result.length);
    return result;
  }

  if (typeof result === 'object' && result !== null) {
    // Check common array field names
    const possibleArrayFields = ['items', 'data', 'results', 'content'];
    for (const field of possibleArrayFields) {
      const value = (result as Record<string, unknown>)[field];
      if (Array.isArray(value)) {
        console.log('[extractArrayFromResult] Found array in field:', field, 'length:', value.length);
        return value;
      }
    }
    console.log('[extractArrayFromResult] No array found in fields:', possibleArrayFields);
    console.log('[extractArrayFromResult] Available fields:', Object.keys(result));
  }

  return null;
}

/**
 * Compares multiple process instance result sets (e.g., active vs completed)
 */
function compareProcessInstances(
  results: Array<{toolName: string; result: unknown; label?: string}>
): VisualizationData | null {
  console.log('[compareProcessInstances] Comparing', results.length, 'process instance sets');
  
  const allSeries: TimeSeriesDataPoint[] = [];
  let minTime = Infinity;
  let maxTime = -Infinity;

  for (const {result, label} of results) {
    const data = extractArrayFromResult(result);
    if (!data || data.length === 0) continue;

    const items = data.filter(
      (item): item is ProcessInstanceItem =>
        typeof item === 'object' && item !== null && 
        ('startDate' in item || 'creationTime' in item || 'timestamp' in item || 'created' in item)
    );

    if (items.length === 0) continue;

    // Determine group label
    const groupLabel = label || detectGroupLabel(items, 'process');

    // Collect timestamps for granularity
    const timestamps = items
      .map(item => item.startDate || (item as any).creationTime || (item as any).timestamp || (item as any).created)
      .filter(Boolean)
      .map(ts => parseISO(ts as string).getTime());

    timestamps.forEach(t => {
      minTime = Math.min(minTime, t);
      maxTime = Math.max(maxTime, t);
    });

    // Group by time
    const timeCounts = new Map<string, number>();
    const timeSpanMs = maxTime - minTime;
    const useMinuteGranularity = timeSpanMs < 60 * 60 * 1000;
    const useHourGranularity = timeSpanMs < 24 * 60 * 60 * 1000;

    items.forEach((item) => {
      const timestamp = item.startDate || (item as any).creationTime || (item as any).timestamp || (item as any).created;
      
      if (timestamp && typeof timestamp === 'string') {
        try {
          const date = parseISO(timestamp);
          let timeKey: string;
          
          if (useMinuteGranularity) {
            timeKey = format(date, 'yyyy-MM-dd HH:mm');
          } else if (useHourGranularity) {
            timeKey = format(date, 'yyyy-MM-dd HH:00');
          } else {
            timeKey = format(startOfDay(date), 'yyyy-MM-dd');
          }
          
          timeCounts.set(timeKey, (timeCounts.get(timeKey) || 0) + 1);
        } catch {
          // Skip invalid dates
        }
      }
    });

    // Convert to chart data points
    timeCounts.forEach((count, timeStr) => {
      allSeries.push({
        group: groupLabel,
        date: parseISO(timeStr),
        value: count,
      });
    });
  }

  if (allSeries.length === 0) return null;

  const timeSpanMs = maxTime - minTime;
  const useMinuteGranularity = timeSpanMs < 60 * 60 * 1000;
  const useHourGranularity = timeSpanMs < 24 * 60 * 60 * 1000;
  const granularityLabel = useMinuteGranularity ? 'by Minute' : useHourGranularity ? 'by Hour' : 'by Day';

  return {
    type: 'timeline',
    title: `Process Instances Comparison (${granularityLabel})`,
    data: allSeries,
    options: {
      axes: {
        bottom: {
          title: 'Time',
          mapsTo: 'date',
          scaleType: ScaleTypes.TIME,
        },
        left: {
          title: 'Number of Instances',
          mapsTo: 'value',
          scaleType: ScaleTypes.LINEAR,
        },
      },
      height: '250px',
      width: '100%',
      resizable: true,
      curve: 'curveMonotoneX',
      points: {
        enabled: true,
        radius: 4,
      },
      timeScale: {
        addSpaceOnEdges: 1,
      },
    },
  };
}

/**
 * Compares multiple incident result sets (e.g., resolved vs active)
 */
function compareIncidents(
  results: Array<{toolName: string; result: unknown; label?: string}>
): VisualizationData | null {
  console.log('[compareIncidents] Comparing', results.length, 'incident sets');
  
  const allSeries: TimeSeriesDataPoint[] = [];
  let minTime = Infinity;
  let maxTime = -Infinity;

  for (const {result, label} of results) {
    const data = extractArrayFromResult(result);
    if (!data || data.length === 0) continue;

    const items = data.filter(
      (item): item is IncidentItem =>
        typeof item === 'object' && item !== null && 
        ('creationTime' in item || 'creationDate' in item || 'timestamp' in item || 'created' in item)
    );

    if (items.length === 0) continue;

    // Determine group label
    const groupLabel = label || detectGroupLabel(items, 'incident');

    // Collect timestamps for granularity
    const timestamps = items
      .map(item => item.creationTime || (item as any).creationDate || (item as any).timestamp || (item as any).created)
      .filter(Boolean)
      .map(ts => parseISO(ts as string).getTime());

    timestamps.forEach(t => {
      minTime = Math.min(minTime, t);
      maxTime = Math.max(maxTime, t);
    });

    // Group by time
    const timeCounts = new Map<string, number>();
    const timeSpanMs = maxTime - minTime;
    const useMinuteGranularity = timeSpanMs < 60 * 60 * 1000;
    const useHourGranularity = timeSpanMs < 24 * 60 * 60 * 1000;

    items.forEach((item) => {
      const timestamp = item.creationTime || (item as any).creationDate || (item as any).timestamp || (item as any).created;
      
      if (timestamp && typeof timestamp === 'string') {
        try {
          const date = parseISO(timestamp);
          let timeKey: string;
          
          if (useMinuteGranularity) {
            timeKey = format(date, 'yyyy-MM-dd HH:mm');
          } else if (useHourGranularity) {
            timeKey = format(date, 'yyyy-MM-dd HH:00');
          } else {
            timeKey = format(startOfDay(date), 'yyyy-MM-dd');
          }
          
          timeCounts.set(timeKey, (timeCounts.get(timeKey) || 0) + 1);
        } catch (error) {
          console.log('[compareIncidents] Failed to parse date:', timestamp, error);
        }
      }
    });

    // Convert to chart data points
    timeCounts.forEach((count, timeStr) => {
      allSeries.push({
        group: groupLabel,
        date: parseISO(timeStr),
        value: count,
      });
    });
  }

  if (allSeries.length === 0) return null;

  const timeSpanMs = maxTime - minTime;
  const useMinuteGranularity = timeSpanMs < 60 * 60 * 1000;
  const useHourGranularity = timeSpanMs < 24 * 60 * 60 * 1000;
  const granularityLabel = useMinuteGranularity ? 'by Minute' : useHourGranularity ? 'by Hour' : 'by Day';

  return {
    type: 'timeline',
    title: `Incidents Comparison (${granularityLabel})`,
    data: allSeries,
    options: {
      axes: {
        bottom: {
          title: 'Time',
          mapsTo: 'date',
          scaleType: ScaleTypes.TIME,
        },
        left: {
          title: 'Number of Incidents',
          mapsTo: 'value',
          scaleType: ScaleTypes.LINEAR,
        },
      },
      height: '250px',
      width: '100%',
      resizable: true,
      curve: 'curveMonotoneX',
      points: {
        enabled: true,
        radius: 4,
      },
      timeScale: {
        addSpaceOnEdges: 1,
      },
    },
  };
}

/**
 * Compares process instances vs incidents (different entities)
 */
function compareProcessInstancesVsIncidents(
  results: Array<{toolName: string; result: unknown; label?: string}>
): VisualizationData | null {
  console.log('[compareProcessInstancesVsIncidents] Comparing process instances vs incidents');
  
  const allSeries: TimeSeriesDataPoint[] = [];
  let minTime = Infinity;
  let maxTime = -Infinity;

  for (const {toolName, result, label} of results) {
    const data = extractArrayFromResult(result);
    if (!data || data.length === 0) continue;

    const isProcessInstance = toolName === 'searchProcessInstances' || toolName === 'search_process_instances';
    const groupLabel = label || (isProcessInstance ? 'Process Instances' : 'Incidents');

    const items = data.filter((item): item is ProcessInstanceItem | IncidentItem =>
      typeof item === 'object' && item !== null && 
      ('startDate' in item || 'creationTime' in item)
    );

    if (items.length === 0) continue;

    // Collect timestamps
    const timestamps = items
      .map(item => {
        if ('startDate' in item) return item.startDate;
        if ('creationTime' in item) return item.creationTime;
        return null;
      })
      .filter(Boolean)
      .map(ts => parseISO(ts as string).getTime());

    timestamps.forEach(t => {
      minTime = Math.min(minTime, t);
      maxTime = Math.max(maxTime, t);
    });

    // Group by time
    const timeCounts = new Map<string, number>();
    const timeSpanMs = maxTime - minTime;
    const useMinuteGranularity = timeSpanMs < 60 * 60 * 1000;
    const useHourGranularity = timeSpanMs < 24 * 60 * 60 * 1000;

    items.forEach((item) => {
      const timestamp = ('startDate' in item ? item.startDate : undefined) || 
                       ('creationTime' in item ? item.creationTime : undefined);
      
      if (timestamp && typeof timestamp === 'string') {
        try {
          const date = parseISO(timestamp);
          let timeKey: string;
          
          if (useMinuteGranularity) {
            timeKey = format(date, 'yyyy-MM-dd HH:mm');
          } else if (useHourGranularity) {
            timeKey = format(date, 'yyyy-MM-dd HH:00');
          } else {
            timeKey = format(startOfDay(date), 'yyyy-MM-dd');
          }
          
          timeCounts.set(timeKey, (timeCounts.get(timeKey) || 0) + 1);
        } catch {
          // Skip invalid dates
        }
      }
    });

    // Convert to chart data points
    timeCounts.forEach((count, timeStr) => {
      allSeries.push({
        group: groupLabel,
        date: parseISO(timeStr),
        value: count,
      });
    });
  }

  if (allSeries.length === 0) return null;

  const timeSpanMs = maxTime - minTime;
  const useMinuteGranularity = timeSpanMs < 60 * 60 * 1000;
  const useHourGranularity = timeSpanMs < 24 * 60 * 60 * 1000;
  const granularityLabel = useMinuteGranularity ? 'by Minute' : useHourGranularity ? 'by Hour' : 'by Day';

  return {
    type: 'timeline',
    title: `Process Instances vs Incidents (${granularityLabel})`,
    data: allSeries,
    options: {
      axes: {
        bottom: {
          title: 'Time',
          mapsTo: 'date',
          scaleType: ScaleTypes.TIME,
        },
        left: {
          title: 'Count',
          mapsTo: 'value',
          scaleType: ScaleTypes.LINEAR,
        },
      },
      height: '250px',
      width: '100%',
      resizable: true,
      curve: 'curveMonotoneX',
      points: {
        enabled: true,
        radius: 4,
      },
      timeScale: {
        addSpaceOnEdges: 1,
      },
    },
  };
}

/**
 * Detects appropriate group label from data
 */
function detectGroupLabel(items: any[], type: 'process' | 'incident'): string {
  if (items.length === 0) return type === 'process' ? 'Process Instances' : 'Incidents';

  // Check for state field to determine label
  if ('state' in items[0]) {
    const state = items[0].state;
    if (state) {
      return `${state.charAt(0)}${state.slice(1).toLowerCase()} ${type === 'process' ? 'Instances' : 'Incidents'}`;
    }
  }

  return type === 'process' ? 'Process Instances' : 'Incidents';
}
