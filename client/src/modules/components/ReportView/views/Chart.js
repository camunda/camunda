import React from 'react';
import ChartRenderer from 'chart.js';
import ReportBlankSlate from '../ReportBlankSlate';

import {getRelativeValue, uniteResults, calculateLinePosition} from './service';
import {formatters} from 'services';

import {themed} from 'theme';

import './Chart.scss';
import {darkColors, lightColors, numberOfStripes} from './Chart.colors.js';

import './TargetValueChart';

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

      const isTargetLine = targetValue && targetValue.active && type === 'line';

      this.chart = new ChartRenderer(this.container, {
        type: isTargetLine ? 'targetLine' : type,
        data: this.createChartData(data, type, targetValue),
        options: this.createChartOptions(type, data, targetValue),
        plugins: [
          {
            afterDraw: this.drawHorizentalLine
          }
        ]
      });
    };

    createChartData = (data, type, targetValue) => {
      const isCombined = this.props.combined;
      let dataArr = data;
      if (!isCombined) dataArr = [data];

      let datasetsColors = this.createColors(dataArr.length);
      datasetsColors[0] = this.props.configuration.color || this.getColorFor('bar');

      let labels = Object.keys(Object.assign({}, ...dataArr));
      dataArr = uniteResults(dataArr, labels);

      if (type === 'line' && targetValue && targetValue.active) {
        return {
          labels,
          datasets: isCombined
            ? this.createCombinedTargetLineDatasets(dataArr, targetValue, datasetsColors)
            : this.createSingleTargetLineDataset(targetValue, data, datasetsColors[0])
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
          ...this.createDatasetOptions(type, report, targetValue, datasetsColors[index], isCombined)
        };
      });

      return {
        labels,
        datasets
      };
    };

    createCombinedTargetLineDatasets = (dataArr, targetValue, datasetsColors) => {
      return dataArr.reduce((prevDataset, report, i) => {
        return [
          ...prevDataset,
          ...this.createSingleTargetLineDataset(
            targetValue,
            report,
            datasetsColors[i],
            this.props.reportsNames[i]
          )
        ];
      }, []);
    };

    createSingleTargetLineDataset = (targetValue, data, datasetColor, reportName) => {
      const isCombined = this.props.combined;
      const allValues = Object.values(data);

      const datasets = [
        {
          data: allValues,
          borderColor: isCombined ? datasetColor : this.getColorFor('targetBar'),
          pointBorderColor: this.getColorFor('targetBar'),
          backgroundColor: 'transparent',
          legendColor: datasetColor,
          borderWidth: 2,
          renderArea: targetValue.values.isBelow ? 'bottom' : 'top'
        },
        {
          label: reportName,
          data: allValues,
          borderColor: datasetColor,
          backgroundColor: 'transparent',
          legendColor: datasetColor,
          borderWidth: 2,
          renderArea: targetValue.values.isBelow ? 'top' : 'bottom'
        }
      ];

      return datasets;
    };

    drawHorizentalLine = chart => {
      if (chart.options.lineAt) {
        const ctx = chart.chart.ctx;
        const xAxe = chart.scales[chart.options.scales.xAxes[0].id];
        const lineAt = calculateLinePosition(chart);

        ctx.save();
        ctx.strokeStyle = this.getColorFor('targetBar');
        ctx.setLineDash([10, 10]);
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(xAxe.left, lineAt);
        ctx.lineTo(xAxe.right, lineAt);
        ctx.stroke();
        ctx.restore();
      }
    };

    componentDidMount = this.createNewChart;
    componentDidUpdate = this.createNewChart;

    createDatasetOptions = (type, data, targetValue, datasetColor, isCombined) => {
      switch (type) {
        case 'pie':
          return {
            borderColor: this.getColorFor('border'),
            backgroundColor: this.createColors(Object.keys(data).length),
            borderWidth: undefined
          };
        case 'line':
          return {
            borderColor: isCombined ? datasetColor : this.props.configuration.color,
            backgroundColor: 'transparent',
            borderWidth: 2,
            legendColor: datasetColor
          };
        case 'bar':
          const barColor = targetValue
            ? this.determineBarColor(targetValue, data, datasetColor)
            : datasetColor;
          return {
            borderColor: barColor,
            backgroundColor: barColor,
            legendColor: datasetColor,
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
      // added an offset of 50 to avoid red colors
      const offset = 50;
      const startValue = offset;
      const stopValue = 360 - offset;
      const stepSize = ~~((stopValue - startValue) / amount);

      for (let i = 0; i < amount; i++) {
        colors.push(
          `hsl(${i * stepSize + offset}, 65%, ${this.props.theme === 'dark' ? 40 : 50}%)`
        );
      }
      return colors;
    };

    determineBarColor = ({active, values}, data, datasetColor) => {
      if (!active) return datasetColor;

      const barValue = values.dateFormat
        ? convertToMilliseconds(values.target, values.dateFormat)
        : values.target;

      const targetColor = this.props.combined
        ? this.getStripedColor(datasetColor)
        : this.getColorFor('targetBar');

      return Object.values(data).map(height => {
        if (values.isBelow) return height < barValue ? datasetColor : targetColor;
        else return height >= barValue ? datasetColor : targetColor;
      });
    };

    getStripedColor = color => {
      const ctx = this.container.getContext('2d');
      const defaultCanvasWidth = 300;

      // we multiply by 2 here to make the moveto reach x=0 at the end of the loop
      // since we are shifting the stripes to the left by canvaswidth
      for (let i = 0; i < numberOfStripes * 2; i++) {
        const thickness = defaultCanvasWidth / numberOfStripes;
        ctx.beginPath();
        ctx.strokeStyle = i % 2 ? 'transparent' : color;
        ctx.lineWidth = thickness;
        ctx.lineCap = 'round';

        // shift the starting point to the left by defaultCanvasWidth to make lines diagonal
        ctx.moveTo(i * thickness + thickness / 2 - defaultCanvasWidth, 0);
        ctx.lineTo(i * thickness + thickness / 2, defaultCanvasWidth);
        ctx.stroke();
      }

      return ctx.createPattern(this.container, 'repeat');
    };

    createPieOptions = () => {
      return {
        legend: {display: true, labels: {fontColor: this.getColorFor('label')}}
      };
    };

    // Override the default generate legend's labels function
    // This is done to modify the colors retrieval method of the side squares and filter unneeded labels
    generateLegendLabels = chart => {
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
      const isCombined = this.props.combined;
      const targetLine = targetValue ? this.getFormattedTargetValue(targetValue) : 0;
      return {
        ...(this.props.configuration.pointMarkers === false
          ? {elements: {point: {radius: 0}}}
          : {}),
        legend: {
          display: isCombined,
          labels: {
            generateLabels: this.generateLegendLabels
          },
          // prevent hiding datasets when clicking on their legends
          onClick: e => e.stopPropagation()
        },
        scales: {
          yAxes: [
            {
              gridLines: {
                color: this.getColorFor('grid')
              },
              scaleLabel: {
                display: !!this.props.configuration.yLabel,
                labelString: this.props.configuration.yLabel
              },
              ticks: {
                ...(this.props.property === 'duration'
                  ? this.createDurationFormattingOptions(data, targetLine, isCombined)
                  : {}),
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
              scaleLabel: {
                display: !!this.props.configuration.xLabel,
                labelString: this.props.configuration.xLabel
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
            // if pie chart then manually append labels to tooltips
            ...(type === 'pie' ? {beforeLabel: ({index}, {labels}) => labels[index]} : {}),
            label: ({index, datasetIndex}, {datasets}) => {
              const {configuration: {hideAbsoluteValue, hideRelativeValue}} = this.props;
              let formatted = '';
              if (!hideAbsoluteValue)
                formatted = this.props.formatter(datasets[datasetIndex].data[index]);
              let processInstanceCountArr = this.props.processInstanceCount;
              if (
                this.props.property === 'frequency' &&
                processInstanceCountArr &&
                !hideRelativeValue
              ) {
                if (!this.props.combined) processInstanceCountArr = [processInstanceCountArr];

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
              if (type === 'pie') {
                const color = datasetOptions.backgroundColor[tooltipItem.index];
                return {
                  borderColor: color,
                  backgroundColor: color
                };
              }

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
