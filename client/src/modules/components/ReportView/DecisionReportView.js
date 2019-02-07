import React from 'react';
import ReportBlankSlate from './ReportBlankSlate';

import {isEmpty} from './service';
import {Table, Number, Chart} from './views';

import {getConfig} from './service';

const checkReport = props => {
  const {
    report: {data}
  } = props;
  if (isEmpty(data.decisionDefinitionKey) || isEmpty(data.decisionDefinitionVersion)) {
    return 'a Decision Definition';
  } else if (!data.view) {
    return 'an option for ”View”';
  } else if (!data.groupBy) {
    return 'an option for ”Group by”';
  } else if (!data.visualization) {
    return 'an option for ”Visualize as”';
  } else {
    return;
  }
};

const getComponent = visualization => {
  switch (visualization) {
    case 'number':
      return Number;
    case 'table':
      return Table;
    case 'bar':
    case 'line':
    case 'pie':
      return Chart;
    default:
      return ReportBlankSlate;
  }
};

export default function DecisionReportView(props) {
  const somethingMissing = checkReport(props);
  if (somethingMissing)
    return (
      <ReportBlankSlate message={'To display a report, please select ' + somethingMissing + '.'} />
    );

  const {visualization, view} = props.report.data;
  const Component = getComponent(visualization);

  return (
    <div className="component">
      <Component {...getConfig(props, view.property)} />
    </div>
  );
}
