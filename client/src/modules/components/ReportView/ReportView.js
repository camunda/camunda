import React from 'react';

import {ErrorBoundary} from 'components';
import {reportLabelMap} from 'services';
import moment from 'moment';
import ReportBlankSlate from './ReportBlankSlate';

import {Number, Json, Table, Heatmap, Chart} from './views';

import {frequencyFormatter, durationFormatter} from './service';

const defaultErrorMessage = 'Cannot display data for the given report builder settings. Please choose another combination!';

export default class ReportView extends React.Component {
  render() {

    if (this.props.report) {
      return this.checkProcDefAndRenderReport(this.props.report);
    } else {
      return (
        <div className='Message Message--error'>{defaultErrorMessage}</div>
      );
    }

  }

  checkProcDefAndRenderReport = (report) => {
    const {data} = report;
    if(this.isEmpty(data.processDefinitionId)) {
      return this.buildInstructionMessage('Process definition');
    } else {
      return this.checkViewAndRenderReport(report);
    }
  }

  checkViewAndRenderReport = report => {
    const {data} = report;
    if(this.isEmpty(data.view.operation)) {
      return this.buildInstructionMessage('View');
    } else if(data.view.operation === 'rawData') {
      return this.checkVisualizationAndRenderReport(report);
    } else {
      return this.checkGroupByAndRenderReport(report);
    }
  }

  checkGroupByAndRenderReport = report => {
    const {data} = report;
    if(this.isEmpty(data.groupBy.type)) {
      return this.buildInstructionMessage('Group by');
    } else {
      return this.checkVisualizationAndRenderReport(report);
    }
  }

  checkVisualizationAndRenderReport = report => {
    const {data} = report;
    if(this.isEmpty(data.visualization)) {
      return this.buildInstructionMessage('Visualize as');
    } else {
      return this.renderReport(report);
    }
  }

  buildInstructionMessage = (field) => {
    return (
      <ReportBlankSlate message={'To display a report, please select an option for ”' + field + '”.'} />
    );
  }

  isEmpty = (str) => {
    return (!str || 0 === str.length);
  }

  renderReport = report => {
    const {data, result} = report;
    let config;
    switch(data.visualization) {
      case 'number':
        config = {
          component: Number,
          props: {data: result}
        }; break;
      case 'table':
        const viewLabel = reportLabelMap.objectToLabel(data.view, reportLabelMap.view);
        const groupByLabel = reportLabelMap.objectToLabel(data.groupBy, reportLabelMap.groupBy);
        const formattedResult = this.formatResult(data, result);
        config = {
          component: Table,
          props: {data: formattedResult, labels: [groupByLabel, viewLabel]}
        }; break;
      case 'heat':
        config = {
          component: Heatmap,
          props: {data: result, process: data.processDefinitionId}
        }; break;
      case 'bar':
      case 'line':
      case 'pie':
        config = {
          component: Chart,
          props: {data: result, type: data.visualization, timeUnit: data.groupBy.unit, property: data.view.property}
        }; break;
      default:
        config = {
          component: Json,
          props: {data : {
            data,
            result
            }
          }
        }; break;
    }

    switch(data.view.property) {
      case 'frequency': config.props.formatter = frequencyFormatter; break;
      case 'duration': config.props.formatter = durationFormatter; break;
      default: config.props.formatter = v => v;
    }

    config.props.errorMessage = defaultErrorMessage;
    const Component = config.component;

    return (<ErrorBoundary>
      <Component {...config.props} />
    </ErrorBoundary>);
  }

  formatResult = (data, result) => {
    const groupBy = data.groupBy;
    if (!groupBy.unit || !result) {
      // the result data is no time series
      return result;
    }
    let dateFormat;
    switch(groupBy.unit) {
      case 'hour':
        dateFormat = 'YYYY-MM-DD HH:00:00';
        break;
      case 'day':
      case 'week':
        dateFormat = 'YYYY-MM-DD';
        break;
      case 'month':
        dateFormat = 'MMM YYYY';
        break;
      case 'year':
        dateFormat = 'YYYY';
        break;
      default:
        dateFormat = 'YYYY-MM-DD HH:MM:SS';
    }
    const formattedResult = {};
      Object.keys(result).forEach(key => {
        const formattedDate = moment(key).format(dateFormat);
        formattedResult[formattedDate] = result[key];
      });
    return formattedResult;
  }
}
