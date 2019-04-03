/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {LoadingIndicator} from 'components';
import {getFlowNodeNames} from 'services';
import ReportBlankSlate from './ReportBlankSlate';

import {getFormatter, processResult} from './service';

import {Number, Table, Heatmap, Chart} from './visualizations';

export default class ProcessReportRenderer extends React.Component {
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
    const {report} = this.props;

    if (!this.state.loaded) {
      return <LoadingIndicator />;
    }

    const Component = this.getComponent();

    const props = {
      ...this.props,
      formatter: getFormatter(report.data.view.property),
      flowNodeNames: this.state.flowNodeNames,
      report: {...this.props.report, result: processResult(this.props.report)}
    };

    return (
      <div className="component">
        <Component {...props} />
      </div>
    );
  }

  getComponent = () => {
    switch (this.props.report.data.visualization) {
      case 'number':
        return Number;
      case 'table':
        return Table;
      case 'bar':
      case 'line':
      case 'pie':
        return Chart;
      case 'heat':
        return Heatmap;
      default:
        return ReportBlankSlate;
    }
  };
}
