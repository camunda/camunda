/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import Chart from 'chart.js';

import {themed} from 'theme';

import './ChartRenderer.scss';

export default themed(
  class ChartRenderer extends React.Component {
    storeContainer = container => {
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
);
