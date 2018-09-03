import React from 'react';

import {ErrorBoundary, LoadingIndicator} from 'components';
import {getFlowNodeNames, formatters, reportConfig} from 'services';
import moment from 'moment';
import ReportBlankSlate from './ReportBlankSlate';

import {Number, Table, Heatmap, Chart} from './views';

const {view, groupBy, getLabelFor} = reportConfig;

const defaultErrorMessage =
  'Cannot display data for the given report builder settings!. Please choose another combination!';

export default class ReportView extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      flowNodeNames: null,
      loaded: false
    };
  }

  getProcDefKey = () => {
    return this.props.report.data.processDefinitionKey;
  };

  getProcDefVersion = () => {
    return this.props.report.data.processDefinitionVersion;
  };

  componentDidMount() {
    if (this.props.report.reportType === 'single') {
      const {processDefinitionVersion, processDefinitionKey} = this.getDataFromProps(this.props);
      if (processDefinitionKey && processDefinitionVersion) {
        this.loadFlowNodeNames(processDefinitionKey, processDefinitionVersion);
      }
    } else {
      this.setState({loaded: true});
    }
  }

  getDataFromProps = ({report: {data}}) => data;

  render() {
    const {report} = this.props;
    if (report) {
      return report.reportType === 'single'
        ? this.checkProcDefAndRenderReport(report)
        : this.checkCombinedAndRender(report);
    } else {
      return <div className="Message Message--error">{defaultErrorMessage}</div>;
    }
  }

  checkCombinedAndRender = report => {
    const result = report.result;
    if (result && typeof result === 'object' && Object.keys(result).length)
      return this.renderCombinedReport(report);
    return this.buildInstructionMessage('one or more reports from the list', true);
  };

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
    if (!data.view) {
      return this.buildInstructionMessage('an option for ”View”');
    } else {
      return this.checkGroupByAndRenderReport(report);
    }
  };

  checkGroupByAndRenderReport = report => {
    const {data} = report;
    if (!data.groupBy) {
      return this.buildInstructionMessage('an option for ”Group by”');
    } else {
      return this.checkVisualizationAndRenderReport(report);
    }
  };

  checkVisualizationAndRenderReport = report => {
    const {data} = report;
    if (!data.visualization) {
      return this.buildInstructionMessage('an option for ”Visualize as”');
    } else {
      return this.renderSingleReport(report);
    }
  };

  buildInstructionMessage = (field, isCombined) => {
    return (
      <ReportBlankSlate
        isCombined={isCombined}
        message={'To display a report, please select ' + field + '.'}
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

  async componentDidUpdate(prevProps) {
    if (this.props.report.reportType === 'single') {
      const {
        processDefinitionVersion: nextProcDefVersion,
        processDefinitionKey: nextProcDefKey
      } = this.getDataFromProps(this.props);

      const {
        processDefinitionVersion: prevProcDefVersion,
        processDefinitionKey: prevProcDefKey
      } = this.getDataFromProps(prevProps);

      const procDefKeyChanged = nextProcDefKey && nextProcDefKey !== prevProcDefKey;
      const procDefVersionChanged = nextProcDefVersion && nextProcDefVersion !== prevProcDefVersion;
      if (procDefKeyChanged || procDefVersionChanged) {
        this.setState({loaded: false});
        await this.loadFlowNodeNames(nextProcDefKey, nextProcDefVersion);
        this.setState({loaded: true});
      }
    }
  }

  applyFlowNodeNames = data => {
    if (this.state.flowNodeNames) {
      const chartData = {};
      Object.keys(data).forEach(key => {
        chartData[this.state.flowNodeNames[key] || key] = data[key];
      });

      return chartData;
    }
  };

  renderSingleReport = report => {
    let {data, result, processInstanceCount} = report;

    const visualizations = ['pie', 'line', 'bar', 'table'];
    if (data.view.entity === 'flowNode' && visualizations.includes(data.visualization) && result) {
      result = this.applyFlowNodeNames(result) || result;
    }

    if (!this.state.loaded) {
      return <LoadingIndicator />;
    }

    return this.getConfig(data.visualization, 'single', result, data, processInstanceCount);
  };

  renderCombinedReport = report => {
    const reports = Object.keys(report.result).map(reportId => report.result[reportId]);

    const {result, data, processInstanceCount} = reports[0];
    if (reports.length === 1) {
      return this.getConfig(data.visualization, 'single', result, data, processInstanceCount);
    }
    return this.getConfig(reports[0].data.visualization, 'combined', report.result, data);
  };

  getConfig = (visualization, reportType, result, data, processInstanceCount) => {
    // temporary check
    if (reportType === 'combined' && visualization !== 'table') {
      const config = {
        component: ReportBlankSlate,
        props: {
          message: 'combined bar charts and area charts are not available yet'
        }
      };
      return this.getReportViewTemplate(config, config.component);
    }

    let config;

    switch (visualization) {
      case 'number':
        config = {
          component: Number,
          props: {data: result, targetValue: data.configuration.targetValue}
        };
        break;
      case 'table':
        config = {
          component: Table,
          props: {
            ...this.getTableProps(reportType, result, data, processInstanceCount),
            reportType,
            configuration: data.configuration,
            disableReportScrolling: this.props.disableReportScrolling,
            property: data.view.property
          }
        };
        break;
      case 'heat':
        config = {
          component: Heatmap,
          props: {
            data: result,
            xml: data.configuration.xml,
            alwaysShowTooltips: data.configuration.alwaysShowTooltips,
            targetValue: data.configuration.targetValue,
            property: data.view.property,
            processInstanceCount
          }
        };
        break;
      case 'bar':
      case 'line':
      case 'pie':
        config = {
          component: Chart,
          props: {
            data: this.formatResult(data, result),
            type: data.visualization,
            property: data.view.property,
            processInstanceCount,
            targetValue: data.configuration.targetValue
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

    return this.getReportViewTemplate(config, config.component);
  };

  getReportViewTemplate = (config, Component) => {
    return (
      <ErrorBoundary>
        {this.props.applyAddons ? (
          this.props.applyAddons(Component, config.props)
        ) : (
          <Component {...config.props} />
        )}
      </ErrorBoundary>
    );
  };

  getTableProps(reportType, result, data, processInstanceCount) {
    if (reportType === 'combined') {
      return this.getCombinedProps(result);
    }
    const viewLabel = getLabelFor(view, data.view);
    const groupByLabel = getLabelFor(groupBy, data.groupBy);
    const formattedResult = this.formatResult(data, result);
    return {
      data: formattedResult,
      labels: [groupByLabel, viewLabel],
      processInstanceCount
    };
  }

  getCombinedProps = result => {
    const reports = Object.keys(result).map(reportId => result[reportId]);
    const initialData = {
      labels: [],
      reportsNames: [],
      data: [],
      processInstanceCount: []
    };

    const combinedProps = reports.reduce((prevReport, report) => {
      const {data, result, processInstanceCount, name} = report;

      // build 2d array of all labels
      const viewLabel = getLabelFor(view, data.view);
      const groupByLabel = getLabelFor(groupBy, data.groupBy);
      const labels = [...prevReport.labels, [groupByLabel, viewLabel]];

      // 2d array of all names
      const reportsNames = [...prevReport.reportsNames, name];

      // 2d array of all results
      const formattedResult = this.formatResult(data, result);
      const reportsResult = [...prevReport.data, formattedResult];

      // 2d array of all process instances count
      const reportsProcessInstanceCount = [
        ...prevReport.processInstanceCount,
        processInstanceCount
      ];

      return {
        labels,
        reportsNames,
        data: reportsResult,
        processInstanceCount: reportsProcessInstanceCount
      };
    }, initialData);

    return combinedProps;
  };

  getDateFormat(unit) {
    let dateFormat;
    switch (unit) {
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
      case 'second':
      default:
        dateFormat = 'YYYY-MM-DD HH:mm:ss';
    }
    return dateFormat;
  }

  formatResult = (data, result) => {
    const groupBy = data.groupBy;
    let unit = groupBy.unit;
    if (!unit && groupBy.type === 'startDate') unit = groupBy.value.unit;
    else if (!unit && groupBy.type === 'variable' && groupBy.value.type === 'Date') unit = 'second';

    if (!unit || !result || data.view.operation === 'rawData') {
      // the result data is no time series
      return result;
    }
    const dateFormat = this.getDateFormat(unit);
    const formattedResult = {};
    Object.keys(result)
      .sort((a, b) => {
        // sort descending for tables and ascending for all other visualizations
        if (data.visualization === 'table') {
          return a < b ? 1 : -1;
        } else {
          return a < b ? -1 : 1;
        }
      })
      .forEach(key => {
        const formattedDate = moment(key).format(dateFormat);
        formattedResult[formattedDate] = result[key];
      });
    return formattedResult;
  };
}
