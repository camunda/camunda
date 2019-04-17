/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import CombinedReportRenderer from './CombinedReportRenderer';
import ProcessReportRenderer from './ProcessReportRenderer';
import DecisionReportRenderer from './DecisionReportRenderer';
import SetupNotice from './SetupNotice';
import IncompleteReport from './IncompleteReport';
import NoDataNotice from './NoDataNotice';

import {ErrorBoundary, Message} from 'components';

import {formatters} from 'services';

import {isEmpty} from './service';

import './ReportRenderer.scss';

const errorMessage =
  'Cannot display data for the given report settings. Please choose another combination!';

export default function ReportRenderer(props) {
  const {report, updateReport, isExternal} = props;
  let View;
  if (report) {
    const isDecision = report.reportType === 'decision';

    if (report.combined) {
      View = CombinedReportRenderer;
    } else if (isDecision) {
      View = DecisionReportRenderer;
    } else {
      View = ProcessReportRenderer;
    }

    const somethingMissing = checkReport(report);
    if (somethingMissing) {
      if (isExternal) {
        return <IncompleteReport id={report.id} />;
      } else if (updateReport) {
        return <SetupNotice>{somethingMissing}</SetupNotice>;
      } else {
        return (
          <SetupNotice>
            <p>
              Select the <b>Edit</b> button above.
            </p>
          </SetupNotice>
        );
      }
    }

    const showNoDataMessage = !containsData(report);

    return (
      <ErrorBoundary>
        <div className="ReportRenderer">
          {showNoDataMessage ? (
            <NoDataNotice>
              {updateReport &&
                !report.combined &&
                'To display this report, edit your set-up above.'}
            </NoDataNotice>
          ) : (
            <>
              <View {...props} errorMessage={errorMessage} />
              {report.data.configuration.showInstanceCount && (
                <div className="additionalInfo">
                  Total {isDecision ? 'Evaluation' : 'Instance'}
                  <br />
                  Count:
                  <b>
                    {formatters.frequency(
                      report.result.processInstanceCount || report.result.decisionInstanceCount || 0
                    )}
                  </b>
                </div>
              )}
            </>
          )}
        </div>
      </ErrorBoundary>
    );
  } else {
    return <Message type="error">{errorMessage}</Message>;
  }
}

function containsData(report) {
  if (report.combined) {
    return report.data.reports.length > 0 && Object.values(report.result.data).some(containsData);
  } else {
    return (
      report.result.processInstanceCount ||
      report.result.decisionInstanceCount ||
      (report.data.view.property === 'frequency' && report.data.visualization === 'number')
    );
  }
}

function checkReport({data, reportType, combined}) {
  if (combined) {
    const reports = data.reports;
    if (!reports || !reports.length) {
      return 'To display a report, please select one or more reports from the list.';
    } else {
      return;
    }
  } else {
    return checkSingleReport(data, reportType);
  }
}

function checkSingleReport(data, reportType) {
  if (
    reportType === 'process' &&
    (isEmpty(data.processDefinitionKey) || isEmpty(data.processDefinitionVersion))
  ) {
    return (
      <p>
        Select a <b>Process Definition</b> above.
      </p>
    );
  } else if (
    reportType === 'decision' &&
    (isEmpty(data.decisionDefinitionKey) || isEmpty(data.decisionDefinitionVersion))
  ) {
    return (
      <p>
        Select a <b>Decision Definition</b> above.
      </p>
    );
  } else if (!data.view) {
    return (
      <p>
        Select an option for <b>View</b> above.
      </p>
    );
  } else if (!data.groupBy) {
    return (
      <p>
        Select what to <b>Group by</b> above.
      </p>
    );
  } else if (!data.visualization) {
    return (
      <p>
        Select an option for <b>Visualize as</b> above.
      </p>
    );
  } else {
    return;
  }
}
