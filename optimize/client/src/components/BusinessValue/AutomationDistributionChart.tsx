/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {TooltipItem} from 'chart.js';

// @ts-expect-error no types available
import ChartRenderer from 'components/ReportRenderer/visualizations/Chart/ChartRenderer';
import {t} from 'translation';

import './AutomationDistributionChart.scss';

interface AutomationDistributionChartProps {
  humanTaskCount: number;
  agentTaskCount: number;
  autoTaskCount: number;
}

function buildSegments(humanTaskCount: number, agentTaskCount: number, autoTaskCount: number) {
  return [
    {label: t('businessValue.chart.humanTasks').toString(), value: humanTaskCount, color: '#aec7e9'},
    {label: t('businessValue.chart.aiAgentTasks').toString(), value: agentTaskCount, color: '#6391d2'},
    {label: t('businessValue.chart.systemAutomation').toString(), value: autoTaskCount, color: '#ffbc72'},
  ];
}

function buildConfig(
  segments: ReturnType<typeof buildSegments>,
  total: number
) {
  return {
    type: 'doughnut' as const,
    data: {
      labels: segments.map((s) => s.label),
      datasets: [
        {
          data: segments.map((s) => s.value),
          backgroundColor: segments.map((s) => s.color),
          borderWidth: 1,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {display: false},
        datalabels: {display: false},
        tooltip: {
          callbacks: {
            label: (context: TooltipItem<'doughnut'>) => {
              const value = context.parsed;
              const pct = total > 0 ? ((value / total) * 100).toFixed(1) : '0';
              return `${context.label}: ${value.toLocaleString()} (${pct}%)`;
            },
          },
        },
      },
    },
  };
}

export default function AutomationDistributionChart({
  humanTaskCount,
  agentTaskCount,
  autoTaskCount,
}: AutomationDistributionChartProps) {
  const segments = buildSegments(humanTaskCount, agentTaskCount, autoTaskCount);
  const total = humanTaskCount + agentTaskCount + autoTaskCount;
  const config = buildConfig(segments, total);

  return (
    <div className="AutomationDistributionChart">
      <div className="canvasContainer">
        <ChartRenderer config={config} />
      </div>
      <div className="legend">
        {segments.map(({label, value, color}) => {
          const pct = total > 0 ? ((value / total) * 100).toFixed(1) : '0';
          return (
            <div key={label} className="legendItem">
              <span className="dot" style={{backgroundColor: color}} />
              <span className="label">{label}</span>
              <span className="value">{pct}%</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
