/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {withErrorHandling} from 'HOC';
import {processResult as processSingleReportResult} from 'services';

import {getFormatter} from './service';
import {Table, Chart} from './visualizations';

const getComponent = (visualization) => {
  if (visualization === 'table') {
    return Table;
  } else {
    return Chart;
  }
};

export default withErrorHandling(
  class CombinedReportRenderer extends React.Component {
    render() {
      const {result} = this.props.report;
      const {view, visualization} = Object.values(result.data)[0].data;
      const Component = getComponent(visualization);

      const processedReport = {
        ...this.props.report,
        result: {...this.props.report.result, data: processResult(this.props.report.result.data)},
      };

      return (
        <div className="component">
          <Component
            {...this.props}
            report={processedReport}
            formatter={getFormatter(view.properties[0])}
          />
        </div>
      );
    }
  }
);

function processResult(reports) {
  return Object.entries(reports).reduce((result, [reportId, report]) => {
    result[reportId] = {...report, result: processSingleReportResult(report)};
    return result;
  }, {});
}
