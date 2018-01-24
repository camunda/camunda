import React from 'react';

import {loadReport, getReportName} from '../service';

import {ReportView} from 'components';
import {Link} from 'react-router-dom';

import './DashboardReport.css';

export default class DashboardReport extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: undefined,
      name: undefined
    };

    this.loadReportData();
  }

  loadReportData = async () => {
    this.setState({
      data: await loadReport(this.props.report.id),
      name: await getReportName(this.props.report.id)
    });
  }

  render() {
    if(!this.state.data) {
      return 'loading...'
    }

    return <div className='DashboardReport__wrapper'>
      <div className='DashboardReport__header'>
      {(this.props.viewMode)
        ? <Link to={`/report/${this.props.report.id}`} className='DashboardReport__heading'>{this.state.name}</Link>
        : <h1 className='DashboardReport__heading'>{this.state.name}</h1>}
      </div>
      <div className='DashboardReport__visualization'>
        {this.state.data.errorMessage ?
          this.state.data.errorMessage :
          <ReportView report={this.state.data} />
        }
      </div>
      {this.props.addons && this.props.addons.map(addon =>
        React.cloneElement(addon, {
          report: this.props.report,
          tileDimensions: this.props.tileDimensions
        })
      )}
    </div>
  }
}
