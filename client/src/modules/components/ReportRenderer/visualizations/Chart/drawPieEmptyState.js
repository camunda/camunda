/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const draw = (chartInstance) => {
  if (chartInstance.options.emptyBackgroundColor) {
    const x = chartInstance.chart.canvas.clientWidth / 2,
      y = chartInstance.chart.canvas.clientHeight / 2,
      ctx = chartInstance.chart.ctx,
      legendOffset = chartInstance.chart.chartArea.top / 2;

    ctx.beginPath();
    ctx.arc(x, y + legendOffset, chartInstance.outerRadius, 0, 2 * Math.PI);
    ctx.fillStyle = chartInstance.options.emptyBackgroundColor;
    ctx.fill();
  }
};

const drawPieEmptyState = {
  beforeDatasetsDraw: draw,
  onResize: draw,
};

export default drawPieEmptyState;
