/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {processResult} from 'services';

import {getFormatter} from './service';
import {Number, Table, Heatmap, Chart} from './visualizations';

export default class ProcessReportRenderer extends React.Component {
  render() {
    const {report} = this.props;
    const Component = this.getComponent();
    const props = {
      ...this.props,
      formatter: getFormatter(report.data.view.properties[0]),
      report: {...this.props.report, result: processResult(this.props.report)},
    };

    return (
      <div className="component">
        <Component {...props} />
      </div>
    );
  }

  getComponent = () => {
    switch (this.props.report.data.visualization) {
      case 'number':
        return Number;
      case 'table':
        return Table;
      case 'bar':
      case 'line':
      case 'pie':
      case 'barLine':
        return Chart;
      case 'heat':
        return Heatmap;
      default:
        return;
    }
  };
}
