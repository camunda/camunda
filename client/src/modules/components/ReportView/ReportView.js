import React from 'react';

import {ErrorBoundary, LoadingIndicator} from 'components';
import {getFlowNodeNames, formatters, reportConfig} from 'services';
import moment from 'moment';
import ReportBlankSlate from './ReportBlankSlate';

import {Number, Table, Heatmap, Chart} from './views';

const {view, groupBy, getLabelFor} = reportConfig;

const defaultErrorMessage =
  'Cannot display data for the given report builder settings. Please choose another combination!';

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

  async componentDidMount() {
    let {reportType, result} = this.props.report;
    if (result) {
      if (reportType === 'single') result = {report: this.props.report};

      Object.keys(result).forEach(async key => {
        const {processDefinitionVersion, processDefinitionKey} = result[key].data;
        if (processDefinitionKey && processDefinitionVersion)
          await this.loadFlowNodeNames(processDefinitionKey, processDefinitionVersion);
      });
    }

    this.setState({loaded: true});
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
    if (this.isEmpty(report.data.reportIds))
      return this.buildInstructionMessage('one or more reports from the list');
    return this.renderReport(report);
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
      return this.renderReport(report);
    }
  };

  buildInstructionMessage = field => {
    return <ReportBlankSlate message={'To display a report, please select ' + field + '.'} />;
  };

  isEmpty = str => {
    return !str || 0 === str.length;
  };

  loadFlowNodeNames = async (key, version) => {
    this.setState({
      flowNodeNames: {
        ...this.state.flowNodeNames,
        [key + version]: await getFlowNodeNames(key, version)
      }
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

  applyFlowNodeNames = report => {
    if (this.state.flowNodeNames) {
      const {result, processDefinitionKey, processDefinitionVersion} = report;
      const chartData = {};
      Object.keys(result).forEach(key => {
        chartData[
          this.state.flowNodeNames[processDefinitionKey + processDefinitionVersion][key] || key
        ] =
          result[key];
      });
      return chartData;
    }
  };

  renderReport = report => {
    let {data, result, processInstanceCount} = report;
    let config;
    let reports = [];

    const visualizations = ['pie', 'line', 'bar', 'table'];
    if (report.reportType === 'combined')
      reports = Object.keys(result).map(reportId => result[reportId]);
    else reports = [report];

    result = reports.map(report => {
      const {data, result} = report;
      if (
        data.view.entity === 'flowNode' &&
        visualizations.includes(data.visualization) &&
        result
      ) {
        return this.applyFlowNodeNames(report) || result;
      }
      return result;
    });

    if (!this.state.loaded) {
      return <LoadingIndicator />;
    }

    let Component;

    if (report.reportType === 'single') {
      result = result[0];

      switch (data.visualization) {
        case 'number':
          config = {
            component: Number,
            props: {data: result, targetValue: data.configuration.targetValue}
          };
          break;
        case 'table':
          const viewLabel = getLabelFor(view, data.view);
          const groupByLabel = getLabelFor(groupBy, data.groupBy);
          const formattedResult = this.formatResult(data, result);
          config = {
            component: Table,
            props: {
              data: formattedResult,
              labels: [groupByLabel, viewLabel],
              configuration: data.configuration,
              disableReportScrolling: this.props.disableReportScrolling,
              property: data.view.property,
              processInstanceCount
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
      Component = config.component;
    } else {
      config = {
        component: ReportBlankSlate,
        props: {
          message: 'Empty Combined ReportView'
        }
      };
      Component = config.component;
    }

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
