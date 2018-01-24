import React from 'react';
import ChartRenderer from 'chart.js';
import ReportBlankSlate from '../ReportBlankSlate';

import './Chart.css';

const colors = [
  '#b5152b',
  '#5315b5',
  '#15b5af',
  '#59b515',
  '#b59315'
]

export default class Chart extends React.Component {
  storeContainer = container => {
    this.container = container;
  }

  render() {
    const {data, errorMessage} = this.props;

    let errorMessageFragment = null;
    if(!data || typeof data !== 'object') {
      this.destroyChart();
      errorMessageFragment = <ReportBlankSlate message={errorMessage} />;
    }

    return (<div className='Chart'>
      {errorMessageFragment}
      <canvas ref={this.storeContainer} />
    </div>);
  }

  componentDidMount() {
    const {data, type, timeUnit} = this.props;

    if(!data || typeof data !== 'object') {
      return;
    }
    this.createNewChart(data, type, timeUnit);
  }

  componentWillReceiveProps(nextProps) {
    const newType = nextProps.type;
    const data = nextProps.data;
    const timeUnit = nextProps.timeUnit;

    if(!data || typeof data !== 'object') {
      return;
    }
    this.createNewChart(data, newType, timeUnit);
  }

  destroyChart = () => {
    if(this.chart) {
      this.chart.destroy(); 
    }
  }

  createNewChart = (data, type, timeUnit) => {
    this.destroyChart();

    this.chart = new ChartRenderer(this.container, {
      type,
      data: {
        labels: Object.keys(data),
        datasets: [{
          data: Object.values(data),
          ...this.createDatasetOptions(type)
        }]
      },
      options: this.createChartOptions(type, timeUnit)
    });
  }

  createDatasetOptions = (type) => {
    switch(type) {
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
  }

  createChartOptions = (type, timeUnit) => {
    let options;
    switch(type) {
      case 'pie':
        options = {
          legend: {
            display: true
          }
        };
        break;
      case 'line':
      case 'bar':
        options = {
          legend : {
            display: false
          },
          scales:  timeUnit && {
            xAxes: [{
                type : 'time',
                time: {
                  unit: timeUnit
                }
            }]
          },
        }
        break;
      default:
        options = {};
    }

    if((type === 'line' || type === 'bar') && this.props.property === 'duration') {
      options.scales = options.scales || {};
      options.scales.yAxes = [{
        ticks: {callback: v => this.props.formatter(v)}
      }];
    }

    options  = {
      ...options,
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      tooltips: {callbacks: {
        label: ({index, datasetIndex}, {datasets}) =>
          this.props.formatter(datasets[datasetIndex].data[index])
      }}
    };
    return options;
  }
}
