/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import ChartRenderer from 'chart.js';
import {calculateLinePosition} from '../service';

// add a new chart type "targetLine" to the ChartRenderer library
// see https://www.chartjs.org/docs/latest/developers/charts.html#extending-existing-chart-types for documentation

ChartRenderer.defaults.targetLine = ChartRenderer.defaults.line;

ChartRenderer.controllers.targetLine = ChartRenderer.controllers.line.extend({
  draw: function() {
    const helpers = ChartRenderer.helpers;
    const dataset = this.getDataset();
    const prop = dataset.renderArea;
    const lineAt = calculateLinePosition(this.chart);
    const chart = this.chart;
    const prevValue = chart.chartArea[prop];
    const meta = this.getMeta();
    const points = meta.data || [];
    const area = chart.chartArea;
    const ilen = points.length;

    area[prop] = lineAt;

    helpers.canvas.clipArea(chart.ctx, area);

    if (helpers.valueOrDefault(dataset.showLine, chart.options.showLines)) {
      meta.dataset.draw();
    }

    helpers.canvas.unclipArea(chart.ctx);

    for (let i = 0; i < ilen; i++) {
      if (
        (points[i].getCenterPoint().y < lineAt && prop === 'bottom') ||
        (points[i].getCenterPoint().y >= lineAt && prop === 'top')
      ) {
        points[i].draw(area);
      }
    }

    this.chart.chartArea[prop] = prevValue;
  }
});
