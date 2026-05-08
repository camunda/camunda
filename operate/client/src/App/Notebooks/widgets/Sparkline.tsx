/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';

const ACCENT_COLORS: Record<string, string> = {
  info: 'var(--cds-support-info)',
  success: 'var(--cds-support-success)',
  warning: 'var(--cds-support-warning)',
  error: 'var(--cds-support-error)',
};

type Props = {
  points: number[];
  color?: string;
  width?: number;
  height?: number;
};

/**
 * Hand-rolled inline SVG sparkline. Renders a polyline connecting the given
 * numeric data points, scaled to fit the requested dimensions. A small filled
 * circle marks the last (most recent) point.
 */
const Sparkline: React.FC<Props> = ({
  points,
  color = 'info',
  width = 120,
  height = 32,
}) => {
  if (points.length < 2) {
    return null;
  }

  const strokeColor = ACCENT_COLORS[color] ?? color;
  const padV = 3;

  const minVal = Math.min(...points);
  const maxVal = Math.max(...points);
  const range = maxVal - minVal || 1;

  // Map data index to SVG x-coordinate
  const xScale = (i: number) => (i / (points.length - 1)) * width;
  // Map value to SVG y-coordinate (invert: higher value = lower y)
  const yScale = (v: number) =>
    height - padV - ((v - minVal) / range) * (height - padV * 2);

  const polylinePoints = points
    .map((v, i) => `${xScale(i).toFixed(1)},${yScale(v).toFixed(1)}`)
    .join(' ');

  const lastX = xScale(points.length - 1);
  const lastY = yScale(points[points.length - 1] as number);

  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      aria-hidden="true"
      style={{display: 'block', overflow: 'visible'}}
    >
      <polyline
        points={polylinePoints}
        fill="none"
        stroke={strokeColor}
        strokeWidth="1.5"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      <circle cx={lastX} cy={lastY} r={3} fill={strokeColor} />
    </svg>
  );
};

export {Sparkline};
