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
      const {report: {result}, errorMessage} = this.props;

      let errorMessageFragment = null;
      if (!result || typeof result !== 'object') {
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
      const {report: {result}} = this.props;
      let {report: {data}} = this.props;

      if (!result || typeof result !== 'object') {
        return;
      }

      const {visualization, configuration} = data;
      const view = data.view || Object.values(result)[0].data.view;

      this.destroyChart();

      const chartType = view.operation === 'count' ? 'countChart' : 'durationChart';
      const targetValue = configuration.targetValue.active && configuration.targetValue[chartType];

      const isTargetLine = targetValue && visualization === 'line';
      const chartVisualization = visualization === 'number' ? 'bar' : visualization;

      this.chart = new ChartRenderer(this.container, {
        type: isTargetLine ? 'targetLine' : chartVisualization,
        data: createChartData({
          ...this.props,
          targetValue
        }),
        options: createChartOptions({
          ...this.props,
          targetValue
        }),
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
