/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import ExternalReport from './ExternalReport';
import OptimizeReport from './OptimizeReport';

import './DashboardReport.scss';

export default function DashboardReport({
  report,
  disableReportScrolling,
  disableNameLink,
  addons,
  tileDimensions,
  loadReport
}) {
  const ReportComponent = isExternalReport(report) ? ExternalReport : OptimizeReport;

  return (
    <ReportComponent
      report={report}
      disableReportScrolling={disableReportScrolling}
      disableNameLink={disableNameLink}
      loadReport={loadReport}
    >
      {(props = {}) =>
        addons &&
        addons.map(addon =>
          React.cloneElement(addon, {
            report,
            tileDimensions,
            ...props
          })
        )
      }
    </ReportComponent>
  );
}

function isExternalReport(report) {
  return report.configuration && report.configuration.external;
}
