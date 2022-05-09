/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import ExternalReport from './ExternalReport';
import OptimizeReport from './OptimizeReport';

import './DashboardReport.scss';

export default function DashboardReport({
  report,
  filter = [],
  disableNameLink,
  addons,
  tileDimensions,
  loadReport,
}) {
  const ReportComponent = isExternalReport(report) ? ExternalReport : OptimizeReport;

  return (
    <ReportComponent
      report={report}
      filter={filter}
      disableNameLink={disableNameLink}
      loadReport={loadReport}
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

function isExternalReport(report) {
  return report.configuration && report.configuration.external;
}
