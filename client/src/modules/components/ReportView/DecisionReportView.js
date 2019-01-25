import React from 'react';
import ReportBlankSlate from './ReportBlankSlate';

import {formatResult, isEmpty} from './service';
import {Table} from './views';

const checkReport = props => {
  const {report: {data}} = props;
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

const getConfig = props => {
  const {report, disableReportScrolling, customProps} = props;
  let {result, processInstanceCount, data} = report;
  let config;

  switch (data.visualization) {
    case 'table':
      config = {
        Component: Table,
        props: {
          data: formatResult(data, result),
          processInstanceCount,
          combined: false,
          configuration: data.configuration,
          sorting: data.parameters && data.parameters.sorting,
          disableReportScrolling: disableReportScrolling,
          property: data.view.property
        }
      };
      break;
    default:
      config = {
        Component: ReportBlankSlate,
        props: {
          message: props.defaultErrorMessage
        }
      };
      break;
  }

  config.props.reportType = report.reportType;
  config.props.errorMessage = props.defaultErrorMessage;
  config.props.report = data;

  config.props = {
    ...config.props,
    ...(customProps ? customProps[data.visualization] || {} : {})
  };

  return config;
};

export default function DecisionReportView(props) {
  const somethingMissing = checkReport(props);
  if (somethingMissing)
    return (
      <ReportBlankSlate message={'To display a report, please select ' + somethingMissing + '.'} />
    );

  const {Component, props: componentProps} = getConfig(props);
  return (
    <div className="component">
      <Component {...componentProps} />
    </div>
  );
}
