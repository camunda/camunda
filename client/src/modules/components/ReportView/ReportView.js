import React from 'react';

import {ErrorBoundary, LoadingIndicator} from 'components';
import {getFlowNodeNames, formatters} from 'services';
import ReportBlankSlate from './ReportBlankSlate';

import {getTableProps, getChartProps} from './service';

import {Number, Table, Heatmap, Chart} from './views';

import './ReportView.scss';

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

  componentDidMount() {
    if (!this.props.report.combined && this.props.report.reportType === 'process') {
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
      return !report.combined
        ? this.checkDefinitionAndRenderReport(report)
        : this.checkCombinedAndRender(report);
    } else {
      return <div className="Message Message--error">{defaultErrorMessage}</div>;
    }
  }

  checkCombinedAndRender = report => {
    const result = report.result;
    if (result && typeof result === 'object' && Object.keys(result).length) {
      const {data} = Object.values(report.result)[0];
      const combinedReportData = {...data, configuration: report.data.configuration};
      return this.getConfig(data.visualization, report.combined, report.result, combinedReportData);
    }
    return this.buildInstructionMessage('one or more reports from the list', true);
  };

  checkDefinitionAndRenderReport = report => {
    const {data, reportType: type} = report;

    if (
      type === 'process' &&
      (this.isEmpty(data.processDefinitionKey) || this.isEmpty(data.processDefinitionVersion))
    ) {
      return this.buildInstructionMessage('a Process Definition');
    } else if (
      type === 'decision' &&
      (this.isEmpty(data.decisionDefinitionKey) || this.isEmpty(data.decisionDefinitionVersion))
    ) {
      return this.buildInstructionMessage('a Decision Definition');
    } else if (!type) {
      return this.buildInstructionMessage('a Definition');
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
    if (!this.props.report.combined) {
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
    let {data, result, processInstanceCount, combined} = report;

    const visualizations = ['pie', 'line', 'bar', 'table'];
    if (data.view.entity === 'flowNode' && visualizations.includes(data.visualization) && result) {
      result = this.applyFlowNodeNames(result) || result;
    }

    if (!this.state.loaded) {
      return <LoadingIndicator />;
    }

    return this.getConfig(data.visualization, combined, result, data, processInstanceCount);
  };

  getCombinedNumberData = result => {
    return Object.values(result).map(report => ({
      [report.name]: report.result
    }));
  };

  getConfig = (visualization, combined, result, data, processInstanceCount) => {
    const {report, disableReportScrolling, customProps} = this.props;
    let config;

    switch (visualization) {
      case 'number':
        if (combined) {
          config = {
            component: Chart,
            props: {
              reportsNames: Object.values(result).map(report => report.name),
              data: this.getCombinedNumberData(result),
              combined: true,
              configuration: data.configuration,
              isDate: false,
              type: 'bar',
              property: data.view.property,
              stacked: true,
              targetValue: data.configuration.targetValue
            }
          };
        } else {
          config = {
            component: Number,
            props: {
              data: result,
              targetValue: data.configuration.targetValue,
              precision: data.configuration.precision
            }
          };
        }
        break;
      case 'table':
        config = {
          component: Table,
          props: {
            ...getTableProps(combined, result, data, processInstanceCount),
            combined,
            configuration: data.configuration,
            sorting: data.parameters && data.parameters.sorting,
            disableReportScrolling: disableReportScrolling,
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
            alwaysShowRelative: data.configuration.alwaysShowRelative,
            alwaysShowAbsolute: data.configuration.alwaysShowAbsolute,
            targetValue: data.configuration.heatmapTargetValue,
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
            ...getChartProps(combined, result, data, processInstanceCount, report),
            configuration: data.configuration,
            combined,
            type: visualization,
            property: data.view.property,
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

    config.props.errorMessage = defaultErrorMessage;
    config.props.report = data;

    config.props = {
      ...config.props,
      ...(customProps ? customProps[visualization] || {} : {})
    };

    return this.getReportViewTemplate(
      config,
      config.component,
      data.configuration.showInstanceCount && processInstanceCount
    );
  };

  getReportViewTemplate = (config, Component, processInstanceCount) => {
    return (
      <ErrorBoundary>
        <div className="ReportView">
          <div className="component">
            {this.props.applyAddons ? (
              this.props.applyAddons(Component, config.props)
            ) : (
              <Component {...config.props} />
            )}
          </div>
          {typeof processInstanceCount === 'number' && (
            <div className="additionalInfo">
              Total Instance<br />Count:
              <b>{formatters.frequency(processInstanceCount)}</b>
            </div>
          )}
        </div>
      </ErrorBoundary>
    );
  };
}
