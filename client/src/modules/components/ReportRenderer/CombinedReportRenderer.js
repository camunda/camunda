import React from 'react';
import {getFormatter, processResult as processSingleReportResult} from './service';
import ReportBlankSlate from './ReportBlankSlate';
import {Table, Chart} from './visualizations';

const getComponent = visualization => {
  switch (visualization) {
    case 'number':
      return Chart;
    case 'table':
      return Table;
    case 'bar':
    case 'line':
      return Chart;
    default:
      return ReportBlankSlate;
  }
};

export default function CombinedReportRenderer(props) {
  const {result} = props.report;
  if (result && typeof result === 'object' && Object.keys(result).length) {
    const {view, visualization} = Object.values(result)[0].data;
    const Component = getComponent(visualization);

    const processedReport = {...props.report, result: processResult(props.report.result)};

    return (
      <div className="component">
        <Component {...props} report={processedReport} formatter={getFormatter(view.property)} />
      </div>
    );
  }

  return (
    <ReportBlankSlate
      isCombined
      errorMessage={'To display a report, please select one or more reports from the list.'}
    />
  );
}

function processResult(reports) {
  return Object.entries(reports).reduce((result, [reportId, report]) => {
    result[reportId] = {...report, result: processSingleReportResult(report)};
    return result;
  }, {});
}
