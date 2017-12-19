import React from 'react';
import ChartRenderer from 'chart.js';
import ReportBlankSlate from '../ReportBlankSlate';

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

    return (<div style={{height: '100%', width: '100%'}}>
      {errorMessageFragment}
      <canvas ref={this.storeContainer} />
    </div>);
  }

  componentDidMount() {
    const {data, type, isTimeSeries} = this.props;

    if(!data || typeof data !== 'object') {
      return;
    }
    this.createNewChart(data, type, isTimeSeries);
  }

  componentWillReceiveProps(nextProps) {
    const newType = nextProps.type;
    const data = nextProps.data;
    const isTimeSeries = nextProps.isTimeSeries;

    if(!data || typeof data !== 'object') {
      return;
    }
    this.createNewChart(data, newType, isTimeSeries);
  }

  destroyChart = () => {
    if(this.chart) {
      this.chart.destroy();
    }
  }

  createNewChart = (data, type, isTimeSeries) => {
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
      ...this.createChartOptions(type, isTimeSeries)
    });
  }

  createDatasetOptions = (type) => {
    let datasetOptions;
    switch(type) {
      case 'pie':
        datasetOptions = {
          borderColor: undefined, 
          backgroundColor: colors, 
          borderWidth: undefined
        };
        break;
      case 'line':
      case 'bar':
        datasetOptions = {
          borderColor: '#00a8ff', 
          backgroundColor: '#e5f6ff', 
          borderWidth: 2
        };
        break;
      default:
        datasetOptions = {
          borderColor: undefined, 
          backgroundColor: undefined, 
          borderWidth: undefined
        };
        break;
    }
    return datasetOptions;
  }

  createChartOptions = (type, isTimeSeries) => {
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
          scales:  isTimeSeries && {
            xAxes: [{
                type : 'time'
            }]
          },
        }
        break;
      default:
        options = {};
    }
    options  = {
      ...options,
      responsive: true,
      maintainAspectRatio: false,
      animation: false
    };
    return {options};
  }
}
