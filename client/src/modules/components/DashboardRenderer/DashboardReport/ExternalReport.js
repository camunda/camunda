/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import './ExternalReport.scss';

export default class ExternalReport extends React.Component {
  state = {
    reloadState: 0,
  };

  reloadReport = () => {
    this.setState({reloadState: this.state.reloadState + 1});
  };

  render() {
    const {report, children = () => {}} = this.props;

    if (report.configuration && report.configuration.external) {
      return (
        <div className="ExternalReport DashboardReport__wrapper">
          <iframe
            key={this.state.reloadState}
            title="External Report"
            src={report.configuration.external}
            frameBorder="0"
            style={{width: '100%', height: '100%'}}
          />
          {children({loadReportData: this.reloadReport})}
        </div>
      );
    }
  }
}

ExternalReport.isExternalReport = function (report) {
  return !!report.configuration?.external;
};
