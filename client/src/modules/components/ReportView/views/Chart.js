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
    const {data} = this.props;
    let {errorMessage} = this.props;

    if(!data || typeof data !== 'object') {
      // show error message
      this.destroyChart();
    } else {
      // show chart
      errorMessage = '';
    }

    return (<div style={{height: '100%', width: '100%'}}>
      <p>{errorMessage}</p>
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

    this.chart = new ChartRenderer(this.container, {
      type,
      data: {
        labels: Object.keys(data),
        datasets: [{
          data: Object.values(data),
          backgroundColor: (type === 'pie' && colors) || undefined
        }]
      },
      options: {
        legend: {
          display: type === 'pie'
        },
        responsive: true,
        maintainAspectRatio: false
      }
    });
  }
}
