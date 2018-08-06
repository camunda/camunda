import React from 'react';

import classnames from 'classnames';

import {ReportView, LoadingIndicator} from 'components';
import {Link} from 'react-router-dom';

import './OptimizeReport.css';

export default class OptimizeReport extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: undefined
    };
  }

  componentDidMount() {
    this.loadReportData();
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

  render() {
    if (!this.state.data) {
      return <LoadingIndicator />;
    }

    const {report, disableNameLink, disableReportScrolling, children = () => {}} = this.props;

    return (
      <div className="DashboardReport__wrapper">
        <div className="OptimizeReport__header">
          {disableNameLink ? (
            <span className="OptimizeReport__heading">{this.getName()}</span>
          ) : (
            <Link to={`/report/${report.id}`} className="OptimizeReport__heading">
              {this.getName()}
            </Link>
          )}
        </div>
        <div
          className={classnames('OptimizeReport__visualization', {
            'OptimizeReport__visualization--unscrollable': disableReportScrolling
          })}
        >
          {this.state.data.errorMessage ? (
            this.state.data.errorMessage
          ) : (
            <ReportView disableReportScrolling={disableReportScrolling} report={this.state.data} />
          )}
        </div>
        {children({loadReportData: this.loadReportData})}
      </div>
    );
  }
}
