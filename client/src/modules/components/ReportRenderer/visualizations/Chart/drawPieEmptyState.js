/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const draw = (chartInstance) => {
  if (chartInstance.options.emptyBackgroundColor) {
    const outerRadius = chartInstance._metasets[0].controller.outerRadius;
    const x = chartInstance.canvas.clientWidth / 2,
      y = chartInstance.canvas.clientHeight / 2,
      ctx = chartInstance.ctx,
      legendOffset = chartInstance.chartArea.top / 2;

    ctx.beginPath();
    ctx.arc(x, y + legendOffset, outerRadius, 0, 2 * Math.PI);
    ctx.fillStyle = chartInstance.options.emptyBackgroundColor;
    ctx.fill();
  }
};

const drawPieEmptyState = {
  beforeDatasetsDraw: draw,
  onResize: draw,
};

export default drawPieEmptyState;
