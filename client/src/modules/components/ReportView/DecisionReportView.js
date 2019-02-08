import React from 'react';
import ReportBlankSlate from './ReportBlankSlate';

import {isEmpty} from './service';
import {Table, Number, Chart, DecisionTable} from './views';

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

const getComponent = (groupBy, visualization) => {
  switch (visualization) {
    case 'number':
      return Number;
    case 'table':
      return groupBy === 'matchedRule' ? DecisionTable : Table;
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
      <ReportBlankSlate
        errorMessage={'To display a report, please select ' + somethingMissing + '.'}
      />
    );

  const {
    visualization,
    view,
    groupBy: {type}
  } = props.report.data;
  const Component = getComponent(type, visualization);

  return (
    <div className="component">
      <Component {...getConfig(props, view.property)} />
    </div>
  );
}
