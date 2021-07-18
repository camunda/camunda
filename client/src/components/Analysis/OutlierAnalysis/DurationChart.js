/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Chart} from 'chart.js';

import {t} from 'translation';

import {formatters} from 'services';
import './DurationChart.scss';
const {createDurationFormattingOptions, duration} = formatters;

export default class DurationChart extends React.Component {
  componentDidMount() {
    this.createChart();
  }

  storeContainer = (canvas) => {
    this.canvas = canvas;
  };

  createChart = () => {
    const {data} = this.props;
    const colors = data.map(({outlier}) => (outlier ? '#1991c8' : '#eeeeee'));
    const maxDuration = data && data.length > 0 ? data[data.length - 1].key : 0;

    return new Chart(this.canvas, {
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
              title: this.createTooltipTitle,
              label: ({label}) =>
                t('analysis.outlier.tooltip.tookDuration') + ' ' + duration(label),
            },
            filter: (tooltipItem) => +tooltipItem.formattedValue > 0,
          },
        },
        scales: {
          xAxis: {
            title: {
              display: true,
              text: t('analysis.outlier.detailsModal.axisLabels.duration'),
              font: {weight: 'bold'},
            },
            ticks: {
              ...createDurationFormattingOptions(null, maxDuration),
            },
          },
          yAxis: {
            title: {
              display: true,
              text: t('analysis.outlier.detailsModal.axisLabels.instanceCount'),
              font: {weight: 'bold'},
            },
            ticks: {
              // this is needed due to this bug: https://github.com/chartjs/Chart.js/issues/9390
              // TODO: Remove this after updating chart.js to 3.5
              callback: (v) => v,
            },
          },
        },
      },
    });
  };

  createTooltipTitle = (data) => {
    if (!data.length) {
      return;
    }
    let key = 'common.instance';
    if (this.props.data[data[0].dataIndex].outlier) {
      key = 'analysis.outlier.tooltip.outlier';
    }

    const unitLabel = t(`${key}.label${+data[0].formattedValue !== 1 ? '-plural' : ''}`);

    return data[0].formattedValue + ' ' + unitLabel;
  };

  render() {
    return (
      <div className="DurationChart">
        <canvas ref={this.storeContainer} />
      </div>
    );
  }
}
