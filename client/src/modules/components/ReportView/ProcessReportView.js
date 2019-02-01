import React from 'react';

import {LoadingIndicator} from 'components';
import {getFlowNodeNames, formatters} from 'services';
import ReportBlankSlate from './ReportBlankSlate';

import {isEmpty} from './service';

import {Number, Table, Heatmap, Chart} from './views';

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

  render() {
    const somethingMissing = this.checkReport();
    if (somethingMissing) {
      return (
        <ReportBlankSlate
          message={'To display a report, please select ' + somethingMissing + '.'}
        />
      );
    }

    const {
      report: {data, processInstanceCount}
    } = this.props;

    if (!this.state.loaded) {
      return <LoadingIndicator />;
    }

    const {Component, props} = this.getConfig();
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
            Total Instance
            <br />
            Count:
            <b>{formatters.frequency(processInstanceCount)}</b>
          </div>
        )}
      </>
    );
  }

  checkReport = () => {
    const {
      report: {data}
    } = this.props;

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

  getConfig = () => {
    const {report, disableReportScrolling, customProps, defaultErrorMessage} = this.props;
    const {visualization, view} = report.data;
    const {flowNodeNames} = this.state;
    let config = {props: {}};

    switch (visualization) {
      case 'number':
        config.Component = Number;
        break;
      case 'table':
        config = {
          Component: Table,
          props: {
            disableReportScrolling
          }
        };
        break;
      case 'heat':
        config.Component = Heatmap;
        break;
      case 'bar':
      case 'line':
      case 'pie':
        config.Component = Chart;
        break;
      default:
        config.Component = ReportBlankSlate;
        break;
    }

    switch (view.property) {
      case 'frequency':
        config.props.formatter = formatters.frequency;
        break;
      case 'duration':
        config.props.formatter = formatters.duration;
        break;
      default:
        config.props.formatter = v => v;
    }

    if (visualization !== 'number') {
      config.props.flowNodeNames = flowNodeNames;
    }

    config.props = {
      errorMessage: defaultErrorMessage,
      ...config.props,
      ...(customProps ? customProps[visualization] || {} : {}),
      ...report
    };

    return config;
  };
}
