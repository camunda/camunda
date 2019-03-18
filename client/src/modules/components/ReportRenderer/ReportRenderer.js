import React from 'react';

import CombinedReportRenderer from './CombinedReportRenderer';
import ProcessReportRenderer from './ProcessReportRenderer';
import DecisionReportRenderer from './DecisionReportRenderer';

import {ErrorBoundary, Message} from 'components';

import {formatters} from 'services';

import './ReportRenderer.scss';

const errorMessage =
  'Cannot display data for the given report settings. Please choose another combination!';

export default function ReportRenderer(props) {
  const {report} = props;
  let View;
  if (report) {
    const isDecision = report.reportType === 'decision';

    if (report.combined) View = CombinedReportRenderer;
    else if (isDecision) View = DecisionReportRenderer;
    else View = ProcessReportRenderer;

    return (
      <ErrorBoundary>
        <div className="ReportRenderer">
          <View {...props} errorMessage={errorMessage} />
          {report.data.configuration.showInstanceCount && (
            <div className="additionalInfo">
              Total {isDecision ? 'Evaluation' : 'Instance'}
              <br />
              Count:
              <b>
                {formatters.frequency(
                  report.processInstanceCount || report.decisionInstanceCount || 0
                )}
              </b>
            </div>
          )}
        </div>
      </ErrorBoundary>
    );
  } else {
    return <Message type="error">{errorMessage}</Message>;
  }
}
