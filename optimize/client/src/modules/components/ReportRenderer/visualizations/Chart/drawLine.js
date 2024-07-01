/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getColorFor} from './colorsUtils';
import {calculateLinePosition, getAxesConfig} from './service';

export default function drawLine(isVertical) {
  const groupByAxisId = getAxesConfig(isVertical).groupBy.id;
  return {
    afterDatasetsDraw: function (chart) {
      if (chart.options.lineAt >= 0 && chart.options.lineAt !== false) {
        const ctx = chart.ctx;
        const groupByAxis = chart.scales[groupByAxisId];
        const lineAt = calculateLinePosition(chart);

        ctx.save();
        ctx.strokeStyle = getColorFor('targetBar', true);
        ctx.setLineDash([10, 10]);
        ctx.lineWidth = 2;
        ctx.beginPath();
        if (isVertical) {
          ctx.moveTo(lineAt, groupByAxis.bottom);
          ctx.lineTo(lineAt, groupByAxis.top);
        } else {
          ctx.moveTo(groupByAxis.left, lineAt);
          ctx.lineTo(groupByAxis.right, lineAt);
        }
        ctx.stroke();
        ctx.restore();
      }
    },
  };
}
