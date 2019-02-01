import React from 'react';
import {formatters} from 'services';
import ReportBlankSlate from './ReportBlankSlate';
import {Table, Chart} from './views';

const getConfig = ({report, disableReportScrolling, customProps, defaultErrorMessage}, data) => {
  let config = {props: {}};

  switch (data.visualization) {
    case 'number':
      config.Component = Chart;
      break;
    case 'table':
      config = {
        Component: Table,
        props: {
          disableReportScrolling
        }
      };
      break;
    case 'bar':
    case 'line':
      config.Component = Chart;
      break;
    default:
      config.Component = ReportBlankSlate;
      break;
  }

  switch (data.view.property) {
    case 'frequency':
      config.props.formatter = formatters.frequency;
      break;
    case 'duration':
      config.props.formatter = formatters.duration;
      break;
    default:
      config.props.formatter = v => v;
  }

  config.props = {
    ...config.props,
    ...(customProps ? customProps[data.visualization] || {} : {}),
    ...report,
    errorMessage: defaultErrorMessage
  };

  return config;
};

export default function CombinedReportView(props) {
  const {
    report: {result, data}
  } = props;
  if (result && typeof result === 'object' && Object.keys(result).length) {
    const singleReportData = Object.values(result)[0].data;
    const combinedReportData = {...singleReportData, configuration: data.configuration};
    const {Component, props: componentProps} = getConfig(props, combinedReportData);
    return (
      <div className="component">
        <Component {...componentProps} />
      </div>
    );
  }

  return (
    <ReportBlankSlate
      isCombined={true}
      message={'To display a report, please select one or more reports from the list.'}
    />
  );
}
