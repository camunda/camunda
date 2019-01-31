import React from 'react';
import ChartRenderer from 'chart.js';
import ReportBlankSlate from '../../ReportBlankSlate';

import {drawHorizentalLine, getCombinedChartProps} from './service';

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
      const {result, errorMessage} = this.props;

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
      const {result, combined} = this.props;
      let {data} = this.props;

      if (!result || typeof result !== 'object') {
        return;
      }

      let combinedProps = {};
      if (combined) {
        data = {...Object.values(result)[0].data, ...data};
        combinedProps = getCombinedChartProps(result, data);
        combinedProps.data = data;
      }

      const {visualization, configuration, view} = data;

      this.destroyChart();

      const chartType = view.operation === 'count' ? 'countChart' : 'durationChart';
      const targetValue = configuration.targetValue.active && configuration.targetValue[chartType];

      const isTargetLine = targetValue && visualization === 'line';
      const chartVisualization = visualization === 'number' ? 'bar' : visualization;

      this.chart = new ChartRenderer(this.container, {
        type: isTargetLine ? 'targetLine' : chartVisualization,
        data: createChartData({
          ...this.props,
          targetValue,
          ...combinedProps
        }),
        options: createChartOptions({
          ...this.props,
          targetValue,
          ...combinedProps
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
