/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';

import {Modal} from 'components';

import {formatters} from 'services';

import Chart from 'chart.js';
import './OutlierDetailsModal.scss';
import {t} from 'translation';

const {createDurationFormattingOptions} = formatters;

export default class OutlierDetailsModal extends Component {
  componentDidMount() {
    this.createChart();
  }

  createChart = () => {
    const {data} = this.props.selectedNode;
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
          callbacks: {
            title: this.createTooltipTitle,
            label: ({xLabel}) => ' ' + t('analysis.outlier.tooltip.tookDuration') + ' ' + xLabel
          }
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
    let unit = 'instance';
    if (this.props.selectedNode.data[data[0].index].outlier) {
      unit = 'outlier';
    }

    const unitLabel = t(
      `analysis.outlier.tooltip.${unit}.label${+data[0].value !== 1 ? '-plural' : ''}`
    );

    return data[0].value + ' ' + unitLabel;
  };

  render() {
    const {name, higherOutlier} = this.props.selectedNode;
    return (
      <Modal open size="large" onClose={this.props.onClose} className="OutlierDetailsModal">
        <Modal.Header>{t('analysis.outlier.detailsModal.title', {name})}</Modal.Header>
        <Modal.Content>
          <p className="description">
            {t('analysis.outlier.tooltipText', {
              count: higherOutlier.count,
              percentage: Math.round(higherOutlier.relation * 100)
            })}
          </p>
          <div className="diagram-container">
            <canvas ref={el => (this.canvas = el)} />
          </div>
        </Modal.Content>
      </Modal>
    );
  }
}
