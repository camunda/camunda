import React from 'react';
import ChartRenderer from 'chart.js';

import {loadCorrelationData} from './service';

import './Statistics.css';

export default class Statistics extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: null
    };
  }
  render() {
    if(this.state.data) {
      return <div className="Statistics">
        <div className="Statistics__diagram-container">
          Gateway: {this.props.gateway.name || this.props.gateway.id}
          {' '}/ EndEvent: {this.props.endEvent.name || this.props.endEvent.id}
          {' '}- Amount: {this.state.data.total}
          <canvas ref={node => this.relativeChart = node} />
        </div>
        <div className="Statistics__diagram-container">
          Gateway: {this.props.gateway.name || this.props.gateway.id}
          {' '}- Amount: {Object.keys(this.state.data.followingNodes).reduce((prev, key) => {
            return prev + this.state.data.followingNodes[key].activityCount;
          }, 0)}
          <canvas ref={node => this.absoluteChart = node} />
        </div>
      </div>;
    }

    return null;
  }

  componentDidUpdate(prevProps) {
    if(
      prevProps.gateway !== this.props.gateway ||
      prevProps.endEvent !== this.props.endEvent ||
      prevProps.config.filter !== this.props.config.filter
    ) {
      this.loadCorrelation();
    } else if(this.state.data && prevProps.config.processDefinitionId === this.props.config.processDefinitionId) {
      // relative chart
      if(this.chart1) {
        this.chart1.destroy();
      }
      this.chart1 = this.createChart(this.relativeChart, ({activitiesReached, activityCount}) => {
        return activitiesReached / activityCount || 0
      });

      // absolute chart
      if(this.chart2) {
        this.chart2.destroy();
      }
      this.chart2 = this.createChart(this.absoluteChart, ({activityCount}) => {
        return activityCount || 0
      });
    }
  }

  componentDidMount() {
    this.loadCorrelation();
  }

  loadCorrelation = async () => {
    this.setState({
      data: await loadCorrelationData(
        this.props.config.processDefinitionId,
        this.props.config.filter,
        this.props.gateway.id,
        this.props.endEvent.id
      )
    });
  }

  createChart = (node, dataFct) => new ChartRenderer(node, {
    type: 'bar',
    data: {
      labels: Object.keys(this.state.data.followingNodes),
      datasets: [{
        data: Object.values(this.state.data.followingNodes).map(dataFct),
        borderColor: '#00a8ff',
        backgroundColor: '#e5f6ff',
        borderWidth: 2
      }]
    },
    options: {
      responsive: true,
      animation: false,
      maintainAspectRatio: false,
      legend: {
        display: false
      },
      scales: {
        yAxes: [{
            ticks: {
                beginAtZero: true,
            }
        }]
    },
    }
  });
}
