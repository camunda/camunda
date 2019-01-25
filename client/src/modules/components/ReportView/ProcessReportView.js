import React from 'react';

import {LoadingIndicator} from 'components';
import {getFlowNodeNames, formatters, reportConfig} from 'services';
import ReportBlankSlate from './ReportBlankSlate';

import {formatResult, isEmpty} from './service';

import {Number, Table, Heatmap, Chart} from './views';

const {view, groupBy, getLabelFor} = reportConfig;

export default class ProcessReportView extends React.Component {
  state = {
    flowNodeNames: null,
    loaded: false
  };

  componentDidMount() {
    const {processDefinitionVersion, processDefinitionKey} = this.props.report.data;
    if (processDefinitionKey && processDefinitionVersion) {
      this.loadFlowNodeNames(processDefinitionKey, processDefinitionVersion);
    }
  }

  async componentDidUpdate(prevProps) {
    const {
      processDefinitionVersion: nextProcDefVersion,
      processDefinitionKey: nextProcDefKey
    } = this.props.report.data;

    const {
      processDefinitionVersion: prevProcDefVersion,
      processDefinitionKey: prevProcDefKey
    } = prevProps.report.data;

    const procDefKeyChanged = nextProcDefKey && nextProcDefKey !== prevProcDefKey;
    const procDefVersionChanged = nextProcDefVersion && nextProcDefVersion !== prevProcDefVersion;
    if (procDefKeyChanged || procDefVersionChanged) {
      await this.loadFlowNodeNames(nextProcDefKey, nextProcDefVersion);
    }
  }

  loadFlowNodeNames = async (key, version) => {
    this.setState({loaded: false});
    this.setState({
      flowNodeNames: await getFlowNodeNames(key, version),
      loaded: true
    });
  };

  applyFlowNodeNames = data => {
    if (this.state.flowNodeNames) {
      const chartData = {};
      Object.keys(data).forEach(key => {
        chartData[this.state.flowNodeNames[key] || key] = data[key];
      });

      return chartData;
    }
    return data;
  };

  render() {
    const somethingMissing = this.checkReport();
    if (somethingMissing) {
      return (
        <ReportBlankSlate
          message={'To display a report, please select ' + somethingMissing + '.'}
        />
      );
    }

    const {report: {data, processInstanceCount}} = this.props;
    let {report: {result}} = this.props;

    const visualizations = ['pie', 'line', 'bar', 'table'];
    if (data.view.entity === 'flowNode' && visualizations.includes(data.visualization) && result) {
      result = this.applyFlowNodeNames(result);
    }

    if (!this.state.loaded) {
      return <LoadingIndicator />;
    }

    const {Component, props} = this.getConfig(result);
    return (
      <>
        <div className="component">
          {this.props.applyAddons ? (
            this.props.applyAddons(Component, props)
          ) : (
            <Component {...props} />
          )}
        </div>
        {data.configuration.showInstanceCount && (
          <div className="additionalInfo">
            Total Instance<br />Count:
            <b>{formatters.frequency(processInstanceCount)}</b>
          </div>
        )}
      </>
    );
  }

  checkReport = () => {
    const {report: {data}} = this.props;

    if (isEmpty(data.processDefinitionKey) || isEmpty(data.processDefinitionVersion)) {
      return 'a Process Definition';
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

  getConfig = result => {
    const {report, disableReportScrolling, customProps} = this.props;
    const {data, processInstanceCount} = report;
    let config;

    switch (data.visualization) {
      case 'number':
        config = {
          Component: Number,
          props: {
            data: result,
            targetValue: data.configuration.targetValue,
            precision: data.configuration.precision
          }
        };
        break;
      case 'table':
        config = {
          Component: Table,
          props: {
            data: formatResult(data, result),
            labels: [getLabelFor(groupBy, data.groupBy), getLabelFor(view, data.view)],
            processInstanceCount,
            combined: false,
            configuration: data.configuration,
            sorting: data.parameters && data.parameters.sorting,
            disableReportScrolling: disableReportScrolling,
            property: data.view.property
          }
        };
        break;
      case 'heat':
        config = {
          Component: Heatmap,
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
          Component: Chart,
          props: {
            data: formatResult(data, result),
            processInstanceCount,
            configuration: data.configuration,
            combined: false,
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
            message: this.props.defaultErrorMessage
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

    config.props.errorMessage = this.props.defaultErrorMessage;
    config.props.report = data;

    config.props = {
      ...config.props,
      ...(customProps ? customProps[data.visualization] || {} : {})
    };

    return config;
  };
}
