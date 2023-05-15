/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
}

function DurationChart({data, colors}: DurationChartProps) {
  const canvas = useRef(null);

  const createTooltipTitle = useCallback(
    (tooltipData: TooltipItem<keyof ChartTypeRegistry>[]) => {
      if (!tooltipData.length) {
        return;
      }
      let key = 'common.instance';
      const idx = tooltipData[0]?.dataIndex;
      if (idx && data[idx]?.outlier) {
        key = 'analysis.outlier.tooltip.outlier';
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
                  'analysis.outlier.tooltip.tookDuration.' +
                    (isSingleInstance ? 'singular' : 'plural')
                )} ${duration(label)}`;
              },
            },
            filter: (tooltipItem) => +tooltipItem.formattedValue > 0,
          },
        },
        scales: {
          x: {
            title: {
              display: true,
              text: t('analysis.outlier.detailsModal.axisLabels.duration').toString(),
              font: {weight: 'bold'},
            },
            ticks: {
              ...createDurationFormattingOptions(null, maxDuration),
            },
          },
          y: {
            title: {
              display: true,
              text: t('analysis.outlier.detailsModal.axisLabels.instanceCount').toString(),
              font: {weight: 'bold'},
            },
          },
        },
      },
    });

    return () => chart.destroy();
  }, [createTooltipTitle, data, colors]);

  return (
    <div className="DurationChart">
      <canvas ref={canvas} />
    </div>
  );
}

export default DurationChart;
