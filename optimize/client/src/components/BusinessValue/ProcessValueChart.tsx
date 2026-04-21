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

interface ProcessValueChartProps {
  processes: TopProcess[];
}

export default function ProcessValueChart({processes}: ProcessValueChartProps) {
  const config = useMemo(
    () => ({
      type: 'bar' as const,
      data: {
        labels: processes.map((p) => p.processLabel),
        datasets: [
          {
            label: 'Value Created',
            data: processes.map((p) => p.valueCreated),
            backgroundColor: '#6391d2',
            borderRadius: 4,
            maxBarThickness: 60,
          },
        ],
      },
      options: {
        indexAxis: 'y' as const,
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          x: {
            beginAtZero: true,
            ticks: {
              callback: (value: string | number) => {
                const num = Number(value);
                return num >= 1000 ? `${(num / 1000).toFixed(0)}k` : String(num);
              },
            },
          },
          y: {
            grid: {display: false},
          },
        },
        plugins: {
          legend: {display: false},
          datalabels: {display: false},
          tooltip: {
            callbacks: {
              label: (context: TooltipItem<'bar'>) => {
                const process = processes[context.dataIndex];
                if (!process) {
                  return '';
                }
                return [
                  `Value Created: €${process.valueCreated.toLocaleString()}`,
                  `Instances: ${process.instanceCount.toLocaleString()}`,
                  `Value per Instance: €${process.instanceCount > 0 ? (process.valueCreated / process.instanceCount).toFixed(2) : '0.00'}`,
                ];
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
