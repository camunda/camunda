/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Chart as ChartRenderer, LineController} from 'chart.js';
import {clipArea, valueOrDefault, unclipArea} from 'chart.js/helpers';

import {calculateLinePosition} from '../service';

// add a new chart type "targetLine" to the ChartRenderer library
// see https://www.chartjs.org/docs/latest/developers/charts.html#extending-existing-chart-types for documentation

class targetLine extends LineController {
  draw() {
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

    clipArea(chart.ctx, area);

    if (valueOrDefault(dataset.showLine, chart.options.showLine)) {
      meta.dataset.draw(chart.ctx, area);
    }

    unclipArea(chart.ctx);

    for (let i = 0; i < ilen; i++) {
      if (
        (points[i].getCenterPoint().y < lineAt && prop === 'bottom') ||
        (points[i].getCenterPoint().y >= lineAt && prop === 'top')
      ) {
        points[i].draw(chart.ctx, area);
      }
    }

    this.chart.chartArea[prop] = prevValue;
  }
}
targetLine.id = 'targetLine';
targetLine.defaults = LineController.defaults;

// Stores the controller so that the chart initialization routine can look it up
ChartRenderer.register(targetLine);
