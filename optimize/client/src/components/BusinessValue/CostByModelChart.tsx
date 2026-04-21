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

import type {ModelCost} from './types';

const BAR_COLORS = ['#6391d2', '#aec7e9', '#ffbc72'];

interface CostByModelChartProps {
  costByModel: ModelCost[];
}

export default function CostByModelChart({costByModel}: CostByModelChartProps) {
  const config = useMemo(
    () => ({
      type: 'bar' as const,
      data: {
        labels: costByModel.map((m) => m.modelName),
        datasets: [
          {
            label: 'Cost (€)',
            data: costByModel.map((m) => m.totalCost),
            backgroundColor: costByModel.map((_, i) => BAR_COLORS[i % BAR_COLORS.length]),
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
            grid: {display: false},
          },
          y: {
            beginAtZero: true,
            ticks: {
              callback: (value: string | number) => {
                const num = Number(value);
                return num >= 1000 ? `${(num / 1000).toFixed(0)}k` : String(num);
              },
            },
          },
        },
        plugins: {
          legend: {display: false},
          datalabels: {display: false},
          tooltip: {
            callbacks: {
              label: (context: TooltipItem<'bar'>) => {
                const model = costByModel[context.dataIndex];
                if (!model) {
                  return '';
                }
                return [
                  `Cost: €${model.totalCost.toLocaleString()}`,
                  `Tokens: ${(model.tokenUsage / 1_000_000).toFixed(1)}M`,
                  `Invocations: ${model.invocationCount.toLocaleString()}`,
                ];
              },
            },
          },
        },
      },
    }),
    [costByModel]
  );

  return <ChartRenderer config={config} />;
}
