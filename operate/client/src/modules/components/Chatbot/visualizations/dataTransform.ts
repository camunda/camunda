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
    
    if (timestamp) {
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
    
    if (timestamp) {
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
