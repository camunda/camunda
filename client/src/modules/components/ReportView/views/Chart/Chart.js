import React from 'react';
import ChartRenderer from 'chart.js';
import ReportBlankSlate from '../../ReportBlankSlate';

import {drawHorizentalLine} from './service';

import {themed} from 'theme';

import './Chart.scss';
import createChartData from './createChartData';
import createChartOptions from './createChartOptions';

import './TargetValueChart';

export default themed(
  class Chart extends React.Component {
    storeContainer = container => {
      this.container = container;
    };

    render() {
      const {data, errorMessage} = this.props;

      let errorMessageFragment = null;
      if (!data || typeof data !== 'object') {
        this.destroyChart();
        errorMessageFragment = <ReportBlankSlate message={errorMessage} />;
      }

      return (
        <div className="Chart">
          {errorMessageFragment}
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
      const {data, type} = this.props;

      if (!data || typeof data !== 'object') {
        return;
      }

      this.destroyChart();

      const chartType =
        this.props.report.view.operation === 'count' ? 'countChart' : 'durationChart';
      const targetValue = {
        active: this.props.targetValue.active,
        values: this.props.targetValue[chartType]
      };

      const isTargetLine = targetValue.active && type === 'line';

      this.chart = new ChartRenderer(this.container, {
        type: isTargetLine ? 'targetLine' : type,
        data: createChartData({...this.props, targetValue}),
        options: createChartOptions({...this.props, targetValue}),
        plugins: [
          {
            afterDatasetsDraw: drawHorizentalLine
          }
        ]
      });
    };

    componentDidMount = this.createNewChart;
    componentDidUpdate = this.createNewChart;
  }
);
