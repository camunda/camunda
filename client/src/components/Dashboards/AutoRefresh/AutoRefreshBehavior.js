/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

export default class AutoRefreshBehavior extends React.Component {
  render() {
    return null;
  }

  componentDidMount() {
    if (this.props.interval) {
      this.timer = setInterval(this.props.loadReportData, this.props.interval);
    }
  }

  componentWillUnmount() {
    clearInterval(this.timer);
  }

  componentDidUpdate(prevProps) {
    if (prevProps.interval !== this.props.interval) {
      clearInterval(this.timer);
      if (this.props.interval) {
        this.timer = setInterval(this.props.loadReportData, this.props.interval);
      }
    }
  }
}
