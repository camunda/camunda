/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {parseISO} from 'date-fns';

import zoomIn from './zoomIn';
import drawPieEmptyState from './drawPieEmptyState';
import generateLegends from './generateLegends';
import fadeOnHover from './fadeOnHover';
import drawLine from './drawLine';

export default function createPlugins({updateReport, report: {hyper, data, result}}) {
  const plugins = [
    drawLine(data.configuration.horizontalBar),
    drawPieEmptyState,
    generateLegends,
    fadeOnHover({visualization: data.visualization, isStacked: data.configuration.stackedBar}),
  ];

  if (
    !hyper &&
    updateReport &&
    ['startDate', 'endDate'].includes(data.groupBy.type) &&
    ['line', 'bar'].includes(data.visualization) &&
    !data.configuration.horizontalBar
  ) {
    const dataPoints = result.data.map(({key}) => key);

    const groupByFilterType = {
      startDate: 'instanceStartDate',
      endDate: 'instanceEndDate',
    };

    if (dataPoints.length) {
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
  }

  return plugins;
}
