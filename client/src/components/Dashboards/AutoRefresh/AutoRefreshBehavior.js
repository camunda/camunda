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

  componentWillReceiveProps(nextProps) {
    if (
      nextProps.interval !== this.props.interval ||
      nextProps.loadReportData !== this.props.loadReportData
    ) {
      clearInterval(this.timer);
      this.timer = this.timer = setInterval(nextProps.loadReportData, nextProps.interval);
    }
  }
}
