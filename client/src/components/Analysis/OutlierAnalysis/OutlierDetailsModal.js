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
            title: data => 'Instance Count: ' + data[0].value,
            label: ({xLabel}) => ' Duration: ' + xLabel
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
                labelString: 'Duration'
              }
            }
          ],
          yAxes: [
            {
              scaleLabel: {
                display: true,
                labelString: 'Instance Count'
              }
            }
          ]
        }
      }
    });
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
