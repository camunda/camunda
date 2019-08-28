/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import Chart from 'chart.js';

import {t} from 'translation';

import {formatters} from 'services';
const {createDurationFormattingOptions} = formatters;

export default class DurationChart extends React.Component {
  componentDidMount() {
    this.createChart();
  }

  storeContainer = canvas => {
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
            borderWidth: 2
          }
        ]
      },
      options: {
        responsive: true,
        animation: false,
        maintainAspectRatio: false,
        legend: {
          display: false
        },
        tooltips: {
          intersect: false,
          mode: 'x',
          callbacks: {
            title: this.createTooltipTitle,
            label: ({xLabel}) => ' ' + t('analysis.outlier.tooltip.tookDuration') + ' ' + xLabel
          },
          filter: (tooltipItem, data) => +tooltipItem.value > 0
        },
        scales: {
          xAxes: [
            {
              ticks: {
                ...createDurationFormattingOptions(null, maxDuration)
              },
              scaleLabel: {
                display: true,
                labelString: 'Duration',
                fontStyle: 'bold'
              }
            }
          ],
          yAxes: [
            {
              scaleLabel: {
                display: true,
                labelString: 'Instance Count',
                fontStyle: 'bold'
              }
            }
          ]
        }
      }
    });
  };

  createTooltipTitle = data => {
    if (!data.length) {
      return;
    }
    let unit = 'instance';
    if (this.props.data[data[0].index].outlier) {
      unit = 'outlier';
    }

    const unitLabel = t(
      `analysis.outlier.tooltip.${unit}.label${+data[0].value !== 1 ? '-plural' : ''}`
    );

    return data[0].value + ' ' + unitLabel;
  };

  render() {
    return (
      <div className="diagramContainer">
        <canvas ref={this.storeContainer} />
      </div>
    );
  }
}
