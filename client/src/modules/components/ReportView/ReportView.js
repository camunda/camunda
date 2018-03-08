import React from 'react';

import {ErrorBoundary} from 'components';
import {reportLabelMap, getFlowNodeNames, formatters} from 'services';
import moment from 'moment';
import ReportBlankSlate from './ReportBlankSlate';

import {Number, Table, Heatmap, Chart} from './views';

const defaultErrorMessage =
  'Cannot display data for the given report builder settings. Please choose another combination!';

export default class ReportView extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      flowNodeNames: null,
      loaded: false
    };
    if (this.getProcDefKey() && this.getProcDefVersion()) {
      this.loadFlowNodeNames(this.getProcDefKey(), this.getProcDefVersion());
    }
  }

  getProcDefKey = () => {
    return this.props.report.data.processDefinitionKey;
  };

  getProcDefVersion = () => {
    return this.props.report.data.processDefinitionVersion;
  };

  render() {
    if (this.props.report) {
      return this.checkProcDefAndRenderReport(this.props.report);
    } else {
      return <div className="Message Message--error">{defaultErrorMessage}</div>;
    }
  }

  checkProcDefAndRenderReport = report => {
    const {data} = report;
    if (this.isEmpty(data.processDefinitionKey) || this.isEmpty(data.processDefinitionVersion)) {
      return this.buildInstructionMessage('Process definition');
    } else {
      return this.checkViewAndRenderReport(report);
    }
  };

  checkViewAndRenderReport = report => {
    const {data} = report;
    if (this.isEmpty(data.view.operation)) {
      return this.buildInstructionMessage('View');
    } else if (data.view.operation === 'rawData') {
      return this.checkVisualizationAndRenderReport(report);
    } else {
      return this.checkGroupByAndRenderReport(report);
    }
  };

  checkGroupByAndRenderReport = report => {
    const {data} = report;
    if (this.isEmpty(data.groupBy.type)) {
      return this.buildInstructionMessage('Group by');
    } else {
      return this.checkVisualizationAndRenderReport(report);
    }
  };

  checkVisualizationAndRenderReport = report => {
    const {data} = report;
    if (this.isEmpty(data.visualization)) {
      return this.buildInstructionMessage('Visualize as');
    } else {
      return this.renderReport(report);
    }
  };

  buildInstructionMessage = field => {
    return (
      <ReportBlankSlate
        message={'To display a report, please select an option for ”' + field + '”.'}
      />
    );
  };

  isEmpty = str => {
    return !str || 0 === str.length;
  };

  loadFlowNodeNames = async (key, version) => {
    this.setState({
      flowNodeNames: await getFlowNodeNames(key, version),
      loaded: true
    });
  };

  componentWillReceiveProps(nextProps) {
    const nextProcDefKey = nextProps.report.data.processDefinitionKey;
    const nextProcDefVersion = nextProps.report.data.processDefinitionVersion;
    const procDefKeyChanged = nextProcDefKey && nextProcDefKey !== this.getProcDefKey();
    const procDefVersionChanged =
      nextProcDefVersion && nextProcDefVersion !== this.getProcDefVersion();
    if (procDefKeyChanged || procDefVersionChanged) {
      this.setState({loaded: false});
      this.loadFlowNodeNames(nextProcDefKey, nextProcDefVersion);
    }
  }

  applyFlowNodeNames = data => {
    if (this.state.flowNodeNames) {
      const chartData = {};
      Object.keys(data).forEach(key => {
        chartData[this.state.flowNodeNames[key]] = data[key];
      });

      return chartData;
    }
  };

  renderReport = report => {
    let {data, result} = report;
    let config;

    const visualizations = ['pie', 'line', 'bar', 'table'];
    if (data.view.entity === 'flowNode' && visualizations.includes(data.visualization) && result) {
      result = this.applyFlowNodeNames(result) || result;
    }

    if (!this.state.loaded) {
      return <p>loading...</p>;
    }

    switch (data.visualization) {
      case 'number':
        config = {
          component: Number,
          props: {data: result}
        };
        break;
      case 'table':
        const viewLabel = reportLabelMap.objectToLabel(data.view, reportLabelMap.view);
        const groupByLabel = reportLabelMap.objectToLabel(data.groupBy, reportLabelMap.groupBy);
        const formattedResult = this.formatResult(data, result);
        config = {
          component: Table,
          props: {data: formattedResult, labels: [groupByLabel, viewLabel]}
        };
        break;
      case 'heat':
        config = {
          component: Heatmap,
          props: {
            data: result,
            xml: data.configuration.xml,
            targetValue: data.configuration.targetValue
          }
        };
        break;
      case 'bar':
      case 'line':
      case 'pie':
        config = {
          component: Chart,
          props: {
            data: result,
            type: data.visualization,
            timeUnit: data.groupBy.unit,
            property: data.view.property
          }
        };
        break;
      default:
        config = {
          component: ReportBlankSlate,
          props: {
            message: defaultErrorMessage
          }
        };
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

    config.props.errorMessage = defaultErrorMessage;
    const Component = config.component;

    return (
      <ErrorBoundary>
        <Component {...config.props} />
      </ErrorBoundary>
    );
  };

  formatResult = (data, result) => {
    const groupBy = data.groupBy;
    if (!groupBy.unit || !result || data.view.operation === 'rawData') {
      // the result data is no time series
      return result;
    }
    let dateFormat;
    switch (groupBy.unit) {
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
  };
}
