import React from 'react';
import ChartRenderer from 'chart.js';
import ReportBlankSlate from '../ReportBlankSlate';

import {getRelativeValue, uniteResults, getLineTargetValues} from './service';
import {formatters} from 'services';

import {themed} from 'theme';

import './Chart.scss';
import {darkColors, lightColors} from './Chart.colors.js';

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
        data: this.createChartData(data, type, targetValue),
        options: this.createChartOptions(type, data, targetValue),
        plugins: [
          {
            id: 'horizontalLine',
            afterDraw: this.drawHorizentalLine
          }
        ]
      });
    };

    createChartData = (data, type, targetValue) => {
      const isCombined = this.props.reportType === 'combined';
      let dataArr = data;
      if (!isCombined) dataArr = [data];

      let colors = this.createColors(dataArr.length);
      colors[0] = this.getColorFor('bar');

      let labels = Object.keys(Object.assign({}, ...dataArr));
      dataArr = uniteResults(dataArr, labels);

      if (type === 'line' && targetValue && targetValue.active) {
        if (!isCombined)
          return {
            labels,
            datasets: this.createSingleTargetLineDataset(targetValue, data, colors[0])
          };

        return {
          labels,
          datasets: this.createCombinedTargetLineDatasets(dataArr, targetValue, colors)
        };
      }

      if (this.props.isDate)
        labels.sort((a, b) => {
          return new Date(a) - new Date(b);
        });

      const datasets = dataArr.map((report, index) => {
        return {
          label: this.props.reportsNames && this.props.reportsNames[index],
          data: Object.values(report),
          ...this.createDatasetOptions(type, report, targetValue, colors[index], isCombined)
        };
      });

      return {
        labels,
        datasets
      };
    };

    createCombinedTargetLineDatasets = (dataArr, targetValue, colors) => {
      return dataArr.reduce((prevDataset, report, i) => {
        return [
          ...prevDataset,
          ...this.createSingleTargetLineDataset(
            targetValue,
            report,
            colors[i],
            this.props.reportsNames[i]
          )
        ];
      }, []);
    };

    createSingleTargetLineDataset = (targetValue, data, color, reportName) => {
      const isCombined = this.props.reportType === 'combined';
      const allValues = Object.values(data);
      const targetValues = getLineTargetValues(allValues, targetValue.values);

      const datasets = [
        {
          data: targetValues,
          borderColor: isCombined ? color : this.getColorFor('targetBar'),
          pointBorderColor: this.getColorFor('targetBar'),
          backgroundColor: isCombined ? 'transparent' : this.getColorFor('targetArea'),
          legendColor: color,
          borderWidth: 2,
          lineTension: 0
        },
        {
          label: reportName,
          data: allValues,
          borderColor: color,
          backgroundColor: isCombined ? 'transparent' : this.getColorFor('area'),
          legendColor: color,
          borderWidth: 2,
          lineTension: 0
        }
      ];

      return datasets;
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

    createDatasetOptions = (type, data, targetValue, color, isCombined) => {
      switch (type) {
        case 'pie':
          return {
            borderColor: this.getColorFor('border'),
            backgroundColor: this.createColors(Object.keys(data).length),
            borderWidth: undefined
          };
        case 'line':
          return {
            borderColor: isCombined ? color : this.getColorFor('bar'),
            backgroundColor: isCombined ? 'transparent' : this.getColorFor('area'),
            borderWidth: 2,
            legendColor: color,
            lineTension: 0
          };
        case 'bar':
          const barColor = targetValue ? this.determineBarColor(targetValue, data, color) : color;
          return {
            borderColor: barColor,
            backgroundColor: barColor,
            legendColor: color,
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
      //250 is used instead of 360 to avoid the red colors in the color wheel
      const stepSize = ~~(250 / amount);

      for (let i = 1; i <= amount; i++) {
        colors.push(`hsl(${i * stepSize}, 65%, ${this.props.theme === 'dark' ? 40 : 50}%)`);
      }
      return colors;
    };

    determineBarColor = ({active, values}, data, color) => {
      if (!active) return color;

      const barValue = values.dateFormat
        ? convertToMilliseconds(values.target, values.dateFormat)
        : values.target;

      const targetColor =
        this.props.reportType === 'combined'
          ? this.getStripedColor(color)
          : this.getColorFor('targetBar');
      return Object.values(data).map(height => {
        if (values.isBelow) return height < barValue ? color : targetColor;
        else return height >= barValue ? color : targetColor;
      });
    };

    getStripedColor = color => {
      const ctx = this.container.getContext('2d');

      const numberOfStripes = 100;
      for (let i = 0; i < numberOfStripes * 2; i++) {
        const thickness = 300 / numberOfStripes;
        ctx.beginPath();
        ctx.strokeStyle = i % 2 ? 'transparent' : color;
        ctx.lineWidth = thickness;
        ctx.lineCap = 'round';

        ctx.moveTo(i * thickness + thickness / 2 - 300, 0);
        ctx.lineTo(i * thickness + thickness / 2, 300);
        ctx.stroke();
      }

      return ctx.createPattern(this.container, 'repeat');
    };

    createPieOptions = () => {
      return {
        legend: {display: true, labels: {fontColor: this.getColorFor('label')}}
      };
    };

    // overide the default generate labels function
    generateLabels = chart => {
      const data = chart.data;
      return data.datasets.length
        ? data.datasets
            .map(function(dataset) {
              return {
                text: dataset.label,
                fillStyle: !dataset.backgroundColor.length
                  ? dataset.backgroundColor
                  : dataset.legendColor,
                strokeStyle: dataset.legendColor
              };
            }, this)
            .filter(dataset => {
              return dataset.text;
            })
        : [];
    };

    createBarOptions = (data, targetValue) => {
      const isCombined = this.props.reportType === 'combined';
      const targetLine = targetValue ? this.getFormattedTargetValue(targetValue) : 0;
      return {
        legend: {
          display: isCombined,
          labels: {
            generateLabels: this.generateLabels
          },
          onClick: e => e.stopPropagation()
        },
        scales: {
          yAxes: [
            {
              gridLines: {
                color: this.getColorFor('grid')
              },
              ticks: {
                ...(this.props.property === 'duration' &&
                  this.createDurationFormattingOptions(data, targetLine, isCombined)),
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
              },
              stacked: this.props.stacked
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

    createDurationFormattingOptions = (data, targetLine, isCombined) => {
      // since the duration is given in milliseconds, chart.js cannot create nice y axis
      // ticks. So we define our own set of possible stepSizes and find one that the maximum
      // value of the dataset fits into or the maximum target line value if it is defined.
      let dataMinStep;
      if (isCombined) {
        dataMinStep =
          Math.max(...data.map(reportResult => Math.max(...Object.values(reportResult)))) / 10;
      } else {
        dataMinStep = Math.max(...Object.values(data)) / 10;
      }
      const targetLineMinStep = targetLine / 10;
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
              let processInstanceCountArr = this.props.processInstanceCount;

              if (this.props.property === 'frequency' && processInstanceCountArr) {
                if (this.props.reportType === 'single')
                  processInstanceCountArr = [processInstanceCountArr];

                let processInstanceCount = processInstanceCountArr[datasetIndex];
                // in the case of the line with target value we have 2 datasets for each report
                // we have to divide by 2 to get the right index
                if (type === 'line' && targetValue && targetValue.active) {
                  processInstanceCount = processInstanceCountArr[~~(datasetIndex / 2)];
                }
                return `${formatted} (${getRelativeValue(
                  datasets[datasetIndex].data[index],
                  processInstanceCount
                )})`;
              } else {
                return formatted;
              }
            },
            labelColor: function(tooltipItem, chart) {
              const datasetOptions = chart.data.datasets[tooltipItem.datasetIndex];
              return {
                borderColor: datasetOptions.legendColor,
                backgroundColor: datasetOptions.legendColor
              };
            }
          }
        }
      };
    };
  }
);
