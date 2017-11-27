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

    if(!data || typeof data !== 'object') {
      return <p>{errorMessage}</p>;
    }

    return (<div style={{height: '400px', width: '600px'}}>
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
    const oldType = this.props.type;
    const newType = nextProps.type;
    const data = nextProps.data;

    if( oldType !== newType) {
      this.createNewChart(data, newType);
    }
  }

  createNewChart = (data, type) => {
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
        }
      }
    });
  }
}
