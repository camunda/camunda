import React from 'react';

import CombinedReportRenderer from './CombinedReportRenderer';
import ProcessReportRenderer from './ProcessReportRenderer';
import DecisionReportRenderer from './DecisionReportRenderer';

import {ErrorBoundary, Message} from 'components';

import './ReportRenderer.scss';

const errorMessage =
  'Cannot display data for the given report settings. Please choose another combination!';

export default function ReportRenderer(props) {
  const {report} = props;
  let View;
  if (report) {
    if (report.combined) View = CombinedReportRenderer;
    else if (report.reportType === 'decision') View = DecisionReportRenderer;
    else View = ProcessReportRenderer;

    return (
      <ErrorBoundary>
        <div className="ReportRenderer">
          <View {...props} errorMessage={errorMessage} />
        </div>
      </ErrorBoundary>
    );
  } else {
    return <Message type="error">{errorMessage}</Message>;
  }
}
