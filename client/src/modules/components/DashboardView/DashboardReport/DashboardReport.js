import React from 'react';

import classnames from 'classnames';

import {ReportView} from 'components';
import {Link} from 'react-router-dom';

import './DashboardReport.css';

export default class DashboardReport extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: undefined
    };
  }

  componentDidMount() {
    if (!this.isExternalReport()) {
      this.loadReportData();
    }
  }

  loadReportData = async () => {
    this.setState({
      data: await this.props.loadReport(this.props.report)
    });
  };

  getName = () => {
    const {name, reportDefinition} = this.state.data;

    return name || (reportDefinition && reportDefinition.name);
  };

  isExternalReport = () => {
    const {report} = this.props;
    return report.configuration && report.configuration.external;
  };

  render() {
    if (this.isExternalReport()) {
      return this.renderExternal();
    }
    return this.renderReport();
  }

  renderExternal() {
    const {report, disableReportScrolling, addons, tileDimensions} = this.props;

    if (report.configuration && report.configuration.external) {
      return (
        <div className="DashboardReport__wrapper">
          <iframe
            title="External Report"
            src={report.configuration.external}
            frameBorder="0"
            scrolling={disableReportScrolling ? 'no' : 'yes'}
            style={{width: '100%', height: '100%'}}
          />
          {addons &&
            addons.map(addon =>
              React.cloneElement(addon, {
                report,
                loadReportData: this.loadReportData,
                tileDimensions
              })
            )}
        </div>
      );
    }
  }

  renderReport() {
    if (!this.state.data) {
      return 'loading...';
    }

    const {report, disableNameLink, disableReportScrolling, addons, tileDimensions} = this.props;

    return (
      <div className="DashboardReport__wrapper">
        <div className="DashboardReport__header">
          {disableNameLink ? (
            <span className="DashboardReport__heading">{this.getName()}</span>
          ) : (
            <Link to={`/report/${report.id}`} className="DashboardReport__heading">
              {this.getName()}
            </Link>
          )}
        </div>
        <div
          className={classnames('DashboardReport__visualization', {
            'DashboardReport__visualization--unscrollable': disableReportScrolling
          })}
        >
          {this.state.data.errorMessage ? (
            this.state.data.errorMessage
          ) : (
            <ReportView disableReportScrolling={disableReportScrolling} report={this.state.data} />
          )}
        </div>
        {addons &&
          addons.map(addon =>
            React.cloneElement(addon, {
              report,
              loadReportData: this.loadReportData,
              tileDimensions
            })
          )}
      </div>
    );
  }
}
