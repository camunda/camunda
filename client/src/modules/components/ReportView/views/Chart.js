import React from 'react';
import ChartRenderer from 'chart.js';
import ReportBlankSlate from '../ReportBlankSlate';

import {getRelativeValue, convertToMilliseconds} from './service';

import './Chart.css';

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

  destroyChart = () => {
    if (this.chart) {
      this.chart.destroy();
    }
  };

  createNewChart = () => {
    const {data, type, targetValue} = this.props;

    if (!data || typeof data !== 'object') {
      return;
    }

    this.destroyChart();

    this.chart = new ChartRenderer(this.container, {
      type,
      data: {
        labels: Object.keys(data),
        datasets: [
          {
            data: Object.values(data),
            ...this.createDatasetOptions(type, data, targetValue)
          }
        ]
      },
      options: this.createChartOptions(type, data, targetValue),
      plugins: [
        {
          id: 'horizontalLine',
          afterDraw: this.drawHorizentalLine
        }
      ]
    });
  };

  drawHorizentalLine = chart => {
    if (chart.options.lineAt) {
      let lineAt = chart.options.lineAt;
      const ctxPlugin = chart.chart.ctx;
      const xAxe = chart.scales[chart.options.scales.xAxes[0].id];
      const yAxe = chart.scales[chart.options.scales.yAxes[0].id];

      ctxPlugin.strokeStyle = '#A62A31';
      ctxPlugin.beginPath();
      // calculate the percentage position of the whole axis
      lineAt = lineAt * 100 / yAxe.max;
      // calulate the position in pixel from the top axis
      lineAt = (100 - lineAt) / 100 * yAxe.height + yAxe.top;
      ctxPlugin.moveTo(xAxe.left, lineAt);
      ctxPlugin.lineTo(xAxe.right, lineAt);
      ctxPlugin.stroke();
    }
  };

  componentDidMount = this.createNewChart;
  componentDidUpdate = this.createNewChart;

  createDatasetOptions = (type, data, targetValue) => {
    switch (type) {
      case 'pie':
        return {
          borderColor: undefined,
          backgroundColor: this.createColors(Object.keys(data).length),
          borderWidth: undefined
        };
      case 'line':
        return {
          borderColor: '#1991c8',
          backgroundColor: '#e5f2f8',
          borderWidth: 2
        };
      case 'bar':
        const barColor = this.determinBarColor(targetValue, data);
        return {
          borderColor: barColor,
          backgroundColor: barColor,
          borderWidth: 1
        };
      default:
        return {
          borderColor: undefined,
          backgroundColor: undefined,
          borderWidth: undefined
        };
    }
  };

  createColors = amount => {
    const colors = [];
    const stepSize = ~~(360 / amount);

    for (let i = 0; i < amount; i++) {
      colors.push(`hsl(${i * stepSize}, 70%, 50%)`);
    }
    return colors;
  };

  determinBarColor = ({active, values}, data) => {
    if (!active) return '#1991c8';
    const barValue = values.dateFormat
      ? convertToMilliseconds(values.target, values.dateFormat)
      : values.target;
    return Object.values(data).map(height => {
      if (values.isAbove) return height < barValue ? '#1991c8' : '#A62A31';
      else return height >= barValue ? '#1991c8' : '#A62A31';
    });
  };

  createPieOptions = () => {
    return {
      legend: {display: true}
    };
  };

  createBarOptions = (data, targetValue) => {
    return {
      legend: {display: false},
      scales: {
        yAxes: [
          {
            ticks: {
              ...(this.props.property === 'duration' && this.createDurationFormattingOptions(data)),
              beginAtZero: true
            }
          }
        ]
      },
      // plugin proberty
      lineAt: targetValue ? this.getFormattedTargetValue(targetValue) : 0
    };
  };

  getFormattedTargetValue = ({active, values}) => {
    if (!active) return 0;
    if (!values.dateFormat) return values.target;
    return convertToMilliseconds(values.target, values.dateFormat);
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
      callback: v => v / niceStepSize.base + niceStepSize.unit,
      stepSize: niceStepSize.value
    };
  };

  createChartOptions = (type, data, targetValue) => {
    let options;
    switch (type) {
      case 'pie':
        options = this.createPieOptions();
        break;
      case 'line':
      case 'bar':
        options = this.createBarOptions(data, targetValue);
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
          label: ({index, datasetIndex}, {datasets}) => {
            const formatted = this.props.formatter(datasets[datasetIndex].data[index]);

            if (this.props.property === 'frequency') {
              return `${formatted} (${getRelativeValue(
                datasets[datasetIndex].data[index],
                this.props.processInstanceCount
              )})`;
            } else {
              return formatted;
            }
          }
        }
      }
    };
  };
}
