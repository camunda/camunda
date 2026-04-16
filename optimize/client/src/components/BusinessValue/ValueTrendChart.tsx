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

import type {TrendEntry} from './types';

interface ValueTrendChartProps {
  trend: TrendEntry[];
}

export default function ValueTrendChart({trend}: ValueTrendChartProps) {
  const config = useMemo(() => {
    const labels = trend.map((entry) => {
      const [year, month] = entry.month.split('-');
      return new Date(Number(year), Number(month) - 1).toLocaleDateString(undefined, {
        month: 'short',
        year: '2-digit',
      });
    });

    return {
      type: 'line' as const,
      data: {
        labels,
        datasets: [
          {
            label: 'Value Created',
            data: trend.map((e) => e.valueCreated),
            borderColor: '#aec7e9',
            backgroundColor: 'rgba(174, 199, 233, 0.15)',
            fill: true,
            tension: 0.3,
            pointRadius: 4,
            pointHoverRadius: 6,
          },
          {
            label: 'Baseline Cost Saved',
            data: trend.map((e) => e.baselineCostSaved),
            borderColor: '#6391d2',
            backgroundColor: 'rgba(99, 145, 210, 0.15)',
            fill: true,
            tension: 0.3,
            pointRadius: 4,
            pointHoverRadius: 6,
          },
          {
            label: 'LLM Cost',
            data: trend.map((e) => e.llmCost),
            borderColor: '#ffbc72',
            backgroundColor: 'rgba(255, 188, 114, 0.15)',
            fill: true,
            tension: 0.3,
            pointRadius: 4,
            pointHoverRadius: 6,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: {
          mode: 'index' as const,
          intersect: false,
        },
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
              label: (context: TooltipItem<'line'>) => {
                const value = context.parsed.y;
                return `${context.dataset.label}: €${value.toLocaleString()}`;
              },
            },
          },
        },
      },
    };
  }, [trend]);

  return <ChartRenderer config={config} />;
}
