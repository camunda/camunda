import React from 'react';
import {getConfig} from './service';
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
    return (
      <div className="component">
        <Component {...getConfig(props, view.property)} />
      </div>
    );
  }

  return (
    <ReportBlankSlate
      isCombined={true}
      errorMessage={'To display a report, please select one or more reports from the list.'}
    />
  );
}
