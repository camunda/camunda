/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import ExternalReport from './ExternalReport';
import OptimizeReport from './OptimizeReport';
import TextReport from './TextReport';

import './DashboardReport.scss';

export default function DashboardReport({
  report,
  filter = [],
  disableNameLink,
  customizeReportLink,
  addons,
  tileDimensions,
  loadReport,
  onReportUpdate,
}) {
  let ReportComponent = OptimizeReport;

  if (ExternalReport.isExternalReport(report)) {
    ReportComponent = ExternalReport;
  } else if (TextReport.isTextReport(report)) {
    ReportComponent = TextReport;
  }

  return (
    <ReportComponent
      report={report}
      filter={filter}
      disableNameLink={disableNameLink}
      customizeReportLink={customizeReportLink}
      loadReport={loadReport}
      onReportUpdate={onReportUpdate}
    >
      {(props = {}) =>
        addons &&
        addons.map((addon) =>
          React.cloneElement(addon, {
            report,
            filter,
            tileDimensions,
            ...props,
          })
        )
      }
    </ReportComponent>
  );
}
