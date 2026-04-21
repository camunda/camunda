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
import {t} from 'translation';

import type {AgentTask} from './types';

const BAR_COLORS = ['#6391d2', '#aec7e9', '#ffbc72'];

interface TopAgentTasksChartProps {
  agentTasks: AgentTask[];
}

function buildConfig(agentTasks: AgentTask[]) {
  return {
    type: 'bar' as const,
    data: {
      labels: agentTasks.map((task) => task.agentName),
      datasets: [
        {
          label: t('businessValue.chart.cost').toString(),
          data: agentTasks.map((task) => task.totalCost),
          backgroundColor: agentTasks.map((_, i) => BAR_COLORS[i % BAR_COLORS.length]),
          borderRadius: 4,
          maxBarThickness: 40,
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
              const task = agentTasks[context.dataIndex];
              if (!task) {
                return '';
              }
              return [
                `${t('businessValue.chart.tooltip.cost')}: €${task.totalCost.toLocaleString()}`,
                `${t('businessValue.chart.tooltip.invocations')}: ${task.invocationCount.toLocaleString()}`,
                `${t('businessValue.chart.tooltip.tokens')}: ${(task.tokenUsage / 1_000_000).toFixed(1)}M`,
              ];
            },
          },
        },
      },
    },
  };
}

export default function TopAgentTasksChart({agentTasks}: TopAgentTasksChartProps) {
  const config = useMemo(() => buildConfig(agentTasks), [agentTasks]);
  return <ChartRenderer config={config} />;
}
