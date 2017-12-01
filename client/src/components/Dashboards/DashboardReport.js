import React from 'react';

import {loadReport} from './service';

import {ReportView} from 'components';

export default class DashboardReport extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: undefined
    };

    this.loadReportData();
  }

  loadReportData = async () => {
    this.setState({
      data: await loadReport(this.props.id)
    });
  }

  render() {
    if(!this.state.data) {
      return 'loading...'
    }

    return <ReportView report={this.state.data} />
  }
}
