import React from 'react';
import ChartRenderer from 'chart.js';
import ReportBlankSlate from '../ReportBlankSlate';

import {getRelativeValue} from './service';
import {formatters} from 'services';

import {themed} from 'theme';

import './Chart.css';

const darkColors = {
  bar: '#1991c8',
  targetBar: '#A62A31',
  grid: 'rgba(255,255,255,0.1)',
  label: '#ddd',
  area: '#4d5356',
  border: '#333'
};

const lightColors = {
  bar: '#1991c8',
  targetBar: '#A62A31',
  grid: 'rgba(0,0,0,0.1)',
  label: '#666',
  area: '#e5f2f8',
  border: '#fff'
};

const {convertToMilliseconds} = formatters;

export default themed(
  class Chart extends React.Component {
    storeContainer = container => {
      this.container = container;
    };

    getColorFor = type => {
      if (this.props.theme === 'dark') {
        return darkColors[type];
      } else {
        return lightColors[type];
      }
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

        ctxPlugin.strokeStyle = this.getColorFor('targetBar');
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
            borderColor: this.getColorFor('border'),
            backgroundColor: this.createColors(Object.keys(data).length),
            borderWidth: undefined
          };
        case 'line':
          return {
            borderColor: this.getColorFor('bar'),
            backgroundColor: this.getColorFor('area'),
            borderWidth: 2
          };
        case 'bar':
          const barColor = this.determineBarColor(targetValue, data);
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
        colors.push(`hsl(${i * stepSize}, 65%, ${this.props.theme === 'dark' ? 40 : 50}%)`);
      }
      return colors;
    };

    determineBarColor = ({active, values}, data) => {
      if (!active) return this.getColorFor('bar');
      const barValue = values.dateFormat
        ? convertToMilliseconds(values.target, values.dateFormat)
        : values.target;
      return Object.values(data).map(height => {
        if (values.isBelow)
          return height < barValue ? this.getColorFor('bar') : this.getColorFor('targetBar');
        else return height >= barValue ? this.getColorFor('bar') : this.getColorFor('targetBar');
      });
    };

    createPieOptions = () => {
      return {
        legend: {display: true}
      };
    };

    createBarOptions = (data, targetValue) => {
      const targetLine = targetValue ? this.getFormattedTargetValue(targetValue) : 0;
      return {
        legend: {display: false},
        scales: {
          yAxes: [
            {
              gridLines: {
                color: this.getColorFor('grid')
              },
              ticks: {
                ...(this.props.property === 'duration' &&
                  this.createDurationFormattingOptions(data, targetLine)),
                beginAtZero: true,
                fontColor: this.getColorFor('label'),
                suggestedMax: targetLine
              }
            }
          ],
          xAxes: [
            {
              gridLines: {
                color: this.getColorFor('grid')
              },
              ticks: {
                fontColor: this.getColorFor('label')
              }
            }
          ]
        },
        // plugin proberty
        lineAt: targetLine
      };
    };

    getFormattedTargetValue = ({active, values}) => {
      if (!active) return 0;
      if (!values.dateFormat) return values.target;
      return convertToMilliseconds(values.target, values.dateFormat);
    };

    createDurationFormattingOptions = (data, targetLine) => {
      // since the duration is given in milliseconds, chart.js cannot create nice y axis
      // ticks. So we define our own set of possible stepSizes and find one that the maximum
      // value of the dataset fits into or the maximum target line value if it is defined.
      const targetLineMinStep = targetLine / 10;
      const dataMinStep = Math.max(...Object.values(data)) / 10;
      const minimumStepSize = Math.max(targetLineMinStep, dataMinStep);

      const steps = [
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
        {value: 1000 * 60 * 60 * 24 * 30 * 12, unit: 'y', base: 1000 * 60 * 60 * 24 * 30 * 12},
        {value: 10 * 1000 * 60 * 60 * 24 * 30 * 12, unit: 'y', base: 1000 * 60 * 60 * 24 * 30 * 12}, //10s of years
        {value: 100 * 1000 * 60 * 60 * 24 * 30 * 12, unit: 'y', base: 1000 * 60 * 60 * 24 * 30 * 12} //100s of years
      ];

      const niceStepSize = steps.find(({value}) => value > minimumStepSize);
      if (!niceStepSize) return;

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
);
