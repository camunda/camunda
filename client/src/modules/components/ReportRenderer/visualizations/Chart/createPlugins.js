/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {parseISO} from 'date-fns';

import {drawHorizentalLine} from './service';
import zoomIn from './zoomIn';
import drawPieEmptyState from './drawPieEmptyState';
import generateLegends from './generateLegends';
import fadeOnHover from './fadeOnHover';

export default function createPlugins({updateReport, report: {combined, data, result}}) {
  const plugins = [
    {
      afterDatasetsDraw: drawHorizentalLine,
    },
    drawPieEmptyState,
    generateLegends,
    fadeOnHover({visualization: data.visualization, isStacked: data.configuration.stackedBar}),
  ];

  if (
    !combined &&
    updateReport &&
    ['startDate', 'evaluationDateTime', 'endDate'].includes(data.groupBy.type) &&
    ['line', 'bar'].includes(data.visualization)
  ) {
    const dataPoints = result.data.map(({key}) => key);

    const groupByFilterType = {
      evaluationDateTime: 'evaluationDateTime',
      startDate: 'instanceStartDate',
      endDate: 'instanceEndDate',
    };

    plugins.push(
      zoomIn({
        updateReport,
        filters: data.filter,
        type: groupByFilterType[data.groupBy.type],
        valueRange: {
          min: parseISO(dataPoints[0]),
          max: parseISO(dataPoints[dataPoints.length - 1]),
        },
      })
    );
  }

  return plugins;
}
