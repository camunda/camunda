import React from 'react';

import {ReportView} from 'components';
import {Link} from 'react-router-dom';

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
      data: await this.props.loadReport(this.props.report)
    });
  };

  render() {
    if (!this.state.data) {
      return 'loading...';
    }

    return (
      <div className="DashboardReport__wrapper">
        <div className="DashboardReport__header">
          <Link to={`/report/${this.props.report.id}`} className="DashboardReport__heading">
            {this.state.data.name}
          </Link>
        </div>
        <div className="DashboardReport__visualization">
          {this.state.data.errorMessage ? (
            this.state.data.errorMessage
          ) : (
            <ReportView report={this.state.data} />
          )}
        </div>
        {this.props.addons &&
          this.props.addons.map(addon =>
            React.cloneElement(addon, {
              report: this.props.report,
              loadReportData: this.loadReportData,
              tileDimensions: this.props.tileDimensions
            })
          )}
      </div>
    );
  }
}
