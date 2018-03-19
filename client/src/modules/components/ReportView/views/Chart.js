import React from 'react';
import ChartRenderer from 'chart.js';
import ReportBlankSlate from '../ReportBlankSlate';

import './Chart.css';

const colors = ['#b5152b', '#5315b5', '#15b5af', '#59b515', '#b59315'];

export default class Chart extends React.Component {
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

  componentDidMount() {
    const {data, type, timeUnit} = this.props;

    if (!data || typeof data !== 'object') {
      return;
    }
    this.createNewChart(data, type, timeUnit);
  }

  componentWillReceiveProps(nextProps) {
    const newType = nextProps.type;
    const data = nextProps.data;
    const timeUnit = nextProps.timeUnit;

    if (!data || typeof data !== 'object') {
      return;
    }
    this.createNewChart(data, newType, timeUnit);
  }

  destroyChart = () => {
    if (this.chart) {
      this.chart.destroy();
    }
  };

  createNewChart = (data, type, timeUnit) => {
    this.destroyChart();

    this.chart = new ChartRenderer(this.container, {
      type,
      data: {
        labels: Object.keys(data),
        datasets: [
          {
            data: Object.values(data),
            ...this.createDatasetOptions(type)
          }
        ]
      },
      options: this.createChartOptions(type, timeUnit, data)
    });
  };

  createDatasetOptions = type => {
    switch (type) {
      case 'pie':
        return {
          borderColor: undefined,
          backgroundColor: colors,
          borderWidth: undefined
        };
      case 'line':
      case 'bar':
        return {
          borderColor: '#00a8ff',
          backgroundColor: '#e5f6ff',
          borderWidth: 2
        };
      default:
        return {
          borderColor: undefined,
          backgroundColor: undefined,
          borderWidth: undefined
        };
    }
  };

  createPieOptions = (timeUnit, data) => {
    return {
      legend: {display: true}
    };
  };

  createBarOptions = (timeUnit, data) => {
    return {
      legend: {display: false},
      scales: {
        ...(timeUnit && this.createTimeUnitScaleOptions(timeUnit)),
        yAxes: [
          {
            ...(this.props.property === 'duration' && this.createDurationFormattingOptions(data)),
            beginAtZero: true
          }
        ]
      }
    };
  };

  createTimeUnitScaleOptions = unit => {
    return {
      xAxes: [{type: 'time', time: {unit}}]
    };
  };

  createDurationFormattingOptions = data => {
    // since the duration is given in milliseconds, chart.js cannot create nice y axis
    // ticks. So we define our own set of possible stepSizes and find one that the maximum
    // value of the dataset fits into.
    const minimumStepSize = Math.max(...Object.values(data)) / 10;

    const niceStepSize = [
      {value: 1, unit: 'ms', base: 1},
      {value: 10, unit: 'ms', base: 1},
      {value: 100, unit: 'ms', base: 1},
      {value: 1000, unit: 's', base: 1000},
      {value: 1000 * 10, unit: 's', base: 1000},
      {value: 1000 * 60, unit: 'min', base: 1000 * 60},
      {value: 1000 * 60 * 10, unit: 'min', base: 1000 * 60},
      {value: 1000 * 60 * 60, unit: 'h', base: 1000 * 60 * 60},
      {value: 1000 * 60 * 60 * 6, unit: 'h', base: 1000 * 60 * 60},
      {value: 1000 * 60 * 60 * 24, unit: 'd', base: 1000 * 60 * 60 * 24},
      {value: 1000 * 60 * 60 * 24 * 7, unit: 'wk', base: 1000 * 60 * 60 * 24 * 7},
      {value: 1000 * 60 * 60 * 24 * 30, unit: 'm', base: 1000 * 60 * 60 * 24 * 30},
      {value: 1000 * 60 * 60 * 24 * 30 * 6, unit: 'm', base: 1000 * 60 * 60 * 24 * 30},
      {value: 1000 * 60 * 60 * 24 * 30 * 12, unit: 'y', base: 1000 * 60 * 60 * 24 * 30 * 12}
    ].find(({value}) => value > minimumStepSize);

    return {
      ticks: {
        callback: v => v / niceStepSize.base + niceStepSize.unit,
        stepSize: niceStepSize.value
      }
    };
  };

  createChartOptions = (type, timeUnit, data) => {
    let options;
    switch (type) {
      case 'pie':
        options = this.createPieOptions(timeUnit, data);
        break;
      case 'line':
      case 'bar':
        options = this.createBarOptions(timeUnit, data);
        break;
      default:
        options = {};
    }

    return {
      ...options,
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      tooltips: {
        callbacks: {
          label: ({index, datasetIndex}, {datasets}) =>
            this.props.formatter(datasets[datasetIndex].data[index])
        }
      }
    };
  };
}
