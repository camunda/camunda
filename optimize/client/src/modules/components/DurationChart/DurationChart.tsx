/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useEffect, useCallback} from 'react';
import {Chart, ChartTypeRegistry, TooltipItem} from 'chart.js';

import {t} from 'translation';
import {formatters} from 'services';
import {AnalysisDurationChartEntry} from 'types';

import './DurationChart.scss';

const {createDurationFormattingOptions, duration} = formatters;

interface DurationChartProps {
  data: AnalysisDurationChartEntry[];
  colors: string[];
  isLogharitmic?: boolean;
}

export default function DurationChart({data, colors, isLogharitmic}: DurationChartProps) {
  const canvas = useRef(null);

  const createTooltipTitle = useCallback(
    (tooltipData: TooltipItem<keyof ChartTypeRegistry>[]) => {
      if (!tooltipData.length) {
        return;
      }
      let key = 'common.instance';
      const idx = tooltipData[0]?.dataIndex;
      if (idx && data[idx]?.outlier) {
        key = 'analysis.task.tooltip.outlier';
      }

      const unitLabel = t(`${key}.label${+tooltipData[0]!.formattedValue !== 1 ? '-plural' : ''}`);

      return tooltipData[0]!.formattedValue + ' ' + unitLabel;
    },
    [data]
  );

  useEffect(() => {
    const lastDataElement = data[data.length - 1];
    const maxDuration = lastDataElement ? lastDataElement.key : 0;

    const chart = new Chart(canvas.current!, {
      type: 'bar',
      data: {
        labels: data.map(({key}) => key),
        datasets: [
          {
            data: data.map(({value}) => value),
            borderColor: colors,
            backgroundColor: colors,
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        animation: false,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: false,
          },
          tooltip: {
            intersect: false,
            mode: 'x',
            callbacks: {
              title: createTooltipTitle,
              label: ({label, dataset, dataIndex}) => {
                const isSingleInstance = dataset.data[dataIndex] === 1;
                return ` ${t(
                  'analysis.task.tooltip.tookDuration.' + (isSingleInstance ? 'singular' : 'plural')
                )} ${duration(label)}`;
              },
            },
            filter: (tooltipItem) => parseInt(tooltipItem.formattedValue) > 0,
          },
        },
        scales: {
          x: {
            title: {
              display: true,
              text: t('analysis.task.detailsModal.axisLabels.duration').toString(),
              font: {weight: 'bold'},
            },
            ticks: {
              ...createDurationFormattingOptions(null, maxDuration),
            },
          },
          y: {
            title: {
              display: true,
              text: t('analysis.task.detailsModal.axisLabels.instanceCount').toString(),
              font: {weight: 'bold'},
            },
            type: isLogharitmic ? 'logarithmic' : 'linear',
          },
        },
      },
    });

    return () => chart.destroy();
  }, [createTooltipTitle, data, colors, isLogharitmic]);

  return (
    <div className="DurationChart">
      <canvas ref={canvas} />
    </div>
  );
}
