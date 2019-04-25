/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

export default class ExternalReport extends React.Component {
  state = {
    reloadState: 0
  };

  reloadReport = () => {
    this.setState({reloadState: this.state.reloadState + 1});
  };

  render() {
    const {report, disableReportScrolling, children = () => {}} = this.props;

    if (report.configuration && report.configuration.external) {
      return (
        <div className="DashboardReport__wrapper">
          <iframe
            key={this.state.reloadState}
            title="External Report"
            src={report.configuration.external}
            frameBorder="0"
            scrolling={disableReportScrolling ? 'no' : 'yes'}
            style={{width: '100%', height: '100%'}}
          />
          {children({loadReportData: this.reloadReport})}
        </div>
      );
    }
  }
}
