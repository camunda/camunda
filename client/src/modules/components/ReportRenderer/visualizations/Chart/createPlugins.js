/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {parseISO} from 'date-fns';

import {drawHorizentalLine} from './service';
import zoomIn from './zoomIn';
import showAllTooltips from './showAllTooltips';
import drawPieEmptyState from './drawPieEmptyState';

export default function createPlugins({updateReport, report: {combined, data, result}}) {
  const plugins = [
    {
      afterDatasetsDraw: drawHorizentalLine,
    },
    showAllTooltips,
    drawPieEmptyState,
  ];

  if (
    !combined &&
    updateReport &&
    ['startDate', 'evaluationDateTime', 'endDate'].includes(data.groupBy.type) &&
    ['line', 'bar'].includes(data.visualization)
  ) {
    const dataPoints = result.data.map(({key}) => key);

    plugins.push(
      zoomIn({
        updateReport,
        filters: data.filter,
        type: data.groupBy.type,
        valueRange: {
          min: parseISO(dataPoints[0]),
          max: parseISO(dataPoints[dataPoints.length - 1]),
        },
      })
    );
  }

  return plugins;
}
