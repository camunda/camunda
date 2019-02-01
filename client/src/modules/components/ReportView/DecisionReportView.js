import React from 'react';
import ReportBlankSlate from './ReportBlankSlate';

import {isEmpty} from './service';
import {Table, Number, Chart} from './views';

import {formatters} from 'services';

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

const getConfig = props => {
  const {report, disableReportScrolling, customProps, defaultErrorMessage} = props;
  let {
    data: {visualization}
  } = report;
  let config = {};

  switch (visualization) {
    case 'table':
      config = {
        Component: Table,
        props: {
          disableReportScrolling
        }
      };
      break;
    case 'number':
      config = {
        Component: Number,
        props: {
          formatter: formatters.frequency
        }
      };
      break;
    case 'bar':
    case 'line':
    case 'pie':
      config = {
        Component: Chart,
        props: {
          formatter: formatters.frequency
        }
      };
      break;
    default:
      config.Component = ReportBlankSlate;
      break;
  }

  config.props = {
    errorMessage: defaultErrorMessage,
    ...config.props,
    ...(customProps ? customProps[visualization] || {} : {}),
    report
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
