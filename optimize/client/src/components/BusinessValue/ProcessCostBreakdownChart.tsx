/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import type {TooltipItem} from 'chart.js';

// @ts-expect-error no types available
import ChartRenderer from 'components/ReportRenderer/visualizations/Chart/ChartRenderer';

import type {TopProcess} from './types';

interface ProcessCostBreakdownChartProps {
  processes: TopProcess[];
}

export default function ProcessCostBreakdownChart({processes}: ProcessCostBreakdownChartProps) {
  const config = useMemo(
    () => ({
      type: 'bar' as const,
      data: {
        labels: processes.map((p) => p.processLabel),
        datasets: [
          {
            label: 'LLM Cost',
            data: processes.map((p) => p.llmCost),
            backgroundColor: '#ffbc72',
            borderRadius: 4,
            maxBarThickness: 60,
          },
          {
            label: 'Baseline Cost Saved',
            data: processes.map((p) => p.baselineCostSaved),
            backgroundColor: '#aec7e9',
            borderRadius: 4,
            maxBarThickness: 60,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          x: {
            stacked: true,
            grid: {display: false},
          },
          y: {
            beginAtZero: true,
            stacked: true,
            ticks: {
              callback: (value: string | number) => {
                const num = Number(value);
                return num >= 1000 ? `${(num / 1000).toFixed(0)}k` : String(num);
              },
            },
          },
        },
        plugins: {
          legend: {
            position: 'bottom' as const,
            labels: {
              usePointStyle: true,
              padding: 16,
            },
          },
          datalabels: {display: false},
          tooltip: {
            callbacks: {
              label: (context: TooltipItem<'bar'>) => {
                const value = context.parsed.y;
                return `${context.dataset.label}: €${value.toLocaleString()}`;
              },
            },
          },
        },
      },
    }),
    [processes]
  );

  return <ChartRenderer config={config} />;
}
