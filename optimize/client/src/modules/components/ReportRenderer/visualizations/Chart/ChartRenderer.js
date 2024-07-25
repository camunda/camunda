/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Chart, registerables} from 'chart.js';
import ChartDataLabels from 'chartjs-plugin-datalabels';

import './ChartRenderer.scss';

Chart.defaults.font.family = "'IBM Plex Sans', 'Helvetica Neue', 'Helvetica', 'Arial', sans-serif";

Chart.register(...registerables);

Chart.register(ChartDataLabels);
Chart.defaults.set('plugins.datalabels', {
  align: 'end',
  anchor: 'end',
  backgroundColor: 'rgba(0, 0, 0, 0.7)',
  borderRadius: 4,
  color: 'white',
  font: {
    weight: 'bold',
  },
  padding: 6,
  display: false,
  clamp: true,
});

export default class ChartRenderer extends React.Component {
  storeContainer = (container) => {
    this.container = container;
  };

  render() {
    return (
      <div className="ChartRenderer">
        <canvas ref={this.storeContainer} />
      </div>
    );
  }

  destroyChart = () => {
    if (this.chart) {
      this.chart.destroy();
    }
  };

  createNewChart = () => {
    this.destroyChart();
    this.chart = new Chart(this.container, this.props.config);
  };

  componentDidMount = this.createNewChart;
  componentDidUpdate = this.createNewChart;
}
