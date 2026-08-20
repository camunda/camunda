/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
