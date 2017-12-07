import React from 'react';

import {loadReport} from './service';

import {ReportView} from 'components';

import './DashboardReport.css';

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

    if(this.state.data.errorMessage) {
      return this.state.data.errorMessage;
    }

    return <div>
      <div className='DashboardReport__header'>
        {this.state.data.name}
      </div>
      <div className='DashboardReport__visualization'>
        <ReportView report={this.state.data} />
      </div>
    </div>
  }
}
