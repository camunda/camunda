import React from 'react';

import {ErrorBoundary} from 'components';

import {Number, Json, Table, Heatmap, Chart} from './views';

const defaultErrorMessage = 'Cannot display data for the given report builder settings. Please choose another combination!';

export default class ReportView extends React.Component {
  render() {

    if (this.props.report) {
      return this.checkProcDefAndRenderReport(this.props.report);
    } else {
      return (
        <p>{defaultErrorMessage}</p>
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
    return <p>Cannot display data for the given report builder settings. Please choose an option for '{field}'!</p>;
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
        config = {
          component: Table,
          props: {data: result}
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
          props: {data: result, type: data.visualization}
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

    config.props.errorMessage = defaultErrorMessage;
    const Component = config.component;

    return (<ErrorBoundary>
      <Component {...config.props} />
    </ErrorBoundary>);
  }
}
