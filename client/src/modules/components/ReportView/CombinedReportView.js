import React from 'react';
import {formatters} from 'services';
import ReportBlankSlate from './ReportBlankSlate';

import {getCombinedTableProps, getCombinedChartProps} from './service';

import {Table, Chart} from './views';

const getCombinedNumberData = result => {
  return Object.values(result).map(report => ({
    [report.name]: report.result
  }));
};

const getConfig = (props, data) => {
  const {report, disableReportScrolling, customProps} = props;
  const {result} = report;
  let config;

  switch (data.visualization) {
    case 'number':
      config = {
        Component: Chart,
        props: {
          reportsNames: Object.values(result).map(report => report.name),
          data: getCombinedNumberData(result),
          combined: true,
          configuration: data.configuration,
          isDate: false,
          type: 'bar',
          property: data.view.property,
          stacked: true,
          targetValue: data.configuration.targetValue
        }
      };
      break;
    case 'table':
      config = {
        Component: Table,
        props: {
          ...getCombinedTableProps(result, report),
          combined: true,
          configuration: data.configuration,
          sorting: data.parameters && data.parameters.sorting,
          disableReportScrolling: disableReportScrolling,
          property: data.view.property
        }
      };
      break;
    case 'bar':
    case 'line':
      config = {
        Component: Chart,
        props: {
          ...getCombinedChartProps(result, data, report),
          configuration: data.configuration,
          combined: true,
          type: data.visualization,
          property: data.view.property,
          targetValue: data.configuration.targetValue
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

  config.props.errorMessage = props.defaultErrorMessage;
  config.props.report = data;

  config.props = {
    ...config.props,
    ...(customProps ? customProps[data.visualization] || {} : {})
  };

  return config;
};

export default function CombinedReportView(props) {
  const {report: {result, data}} = props;
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
