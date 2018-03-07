import React from 'react';

export default class AutoRefreshBehavior extends React.Component {
  render() {
    return null;
  }

  componentDidMount() {
    this.timer = setInterval(this.props.loadReportData, this.props.interval);
  }

  componentWillUnmount() {
    clearInterval(this.timer);
  }
}
