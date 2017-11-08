import React from 'react';
import Chart from 'chart.js';

export default class BarChart extends React.Component {
  storeContainer = container => {
    this.container = container;
  }

  render() {
    const {data} = this.props;

    if(!data || typeof data !== 'object') {
      return <p>Cannot display data. Choose another visualization.</p>;
    }

    return (<div style={{height: '400px', width: '600px'}}>
      <canvas ref={this.storeContainer} />
    </div>);
  }

  componentDidMount() {
    const {data} = this.props;

    if(!data || typeof data !== 'object') {
      return;
    }

    this.chart = new Chart(this.container, {
      type: 'bar',
      data: {
        labels: Object.keys(data),
        datasets: [{
          data: Object.values(data)
        }]
      },
      options: {
        legend: {
          display: false
        }
      }
    });
  }
}
