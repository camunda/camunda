import React from 'react';
import ChartRenderer from 'chart.js';

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
      errorMessageFragment = <p>{errorMessage}</p>;
    }

    return (<div style={{height: '100%', width: '100%'}}>
      {errorMessageFragment}
      <canvas ref={this.storeContainer} />
    </div>);
  }

  componentDidMount() {
    const {data, type} = this.props;

    if(!data || typeof data !== 'object') {
      return;
    }
    this.createNewChart(data, type);
  }

  componentWillReceiveProps(nextProps) {
    const newType = nextProps.type;
    const data = nextProps.data;

    if(!data || typeof data !== 'object') {
      return;
    }
    this.createNewChart(data, newType);
  }

  destroyChart = () => {
    if(this.chart) {
      this.chart.destroy();
    }
  }

  createNewChart = (data, type) => {
    this.destroyChart();

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
    

    this.chart = new ChartRenderer(this.container, {
      type,
      data: {
        labels: Object.keys(data),
        datasets: [{
          data: Object.values(data),
          ...datasetOptions
        }]
      },
      options: {
        legend: {
          display: type === 'pie'
        },
        responsive: true,
        maintainAspectRatio: false,
        animation: false
      }
    });
  }
}
