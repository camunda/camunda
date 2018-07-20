import React from 'react';

export default class AutoRefreshBehavior extends React.Component {
  render() {
    return null;
  }

  componentDidMount() {
    this.runTimer();
  }

  componentWillUnmount() {
    clearInterval(this.timer);
  }

  componentWillReceiveProps(nextProps) {
    if (
      nextProps.interval !== this.props.interval ||
      nextProps.loadReportData !== this.props.loadReportData
    ) {
      clearInterval(this.timer);
      this.runTimer();
    }
  }

  runTimer = () => {
    this.timer = setInterval(async () => {
      await this.props.renderDashboard();
      this.props.loadReportData();
    }, this.props.interval);
  };
}
