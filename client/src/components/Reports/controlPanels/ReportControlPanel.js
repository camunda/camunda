/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {DefinitionSelection} from 'components';

import ReportSelect from './ReportSelect';

import {Filter} from './filter';
import {getFlowNodeNames, reportConfig, formatters, loadProcessDefinitionXml} from 'services';

import {TargetValueComparison} from './targetValue';
import {ProcessPart} from './ProcessPart';

import {loadVariables, isDurationHeatmap, isProcessInstanceDuration} from './service';

import {Configuration} from './Configuration';

import './ReportControlPanel.scss';

const {process: processConfig} = reportConfig;

export default class ReportControlPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      variables: [],
      flowNodeNames: null
    };
  }

  componentDidMount() {
    this.loadVariables();
    this.loadFlowNodeNames();
  }

  loadFlowNodeNames = async () => {
    const {
      data: {processDefinitionKey, processDefinitionVersion, tenantIds}
    } = this.props.report;
    this.setState({
      flowNodeNames: await getFlowNodeNames(
        processDefinitionKey,
        processDefinitionVersion,
        tenantIds[0]
      )
    });
  };

  loadVariables = async () => {
    const {processDefinitionKey, processDefinitionVersion} = this.props.report.data;
    if (processDefinitionKey && processDefinitionVersion) {
      this.setState({
        variables: await loadVariables(processDefinitionKey, processDefinitionVersion)
      });
    }
  };

  componentDidUpdate(prevProps) {
    const {data} = this.props.report;
    const {data: prevData} = prevProps.report;

    if (
      data.processDefinitionKey !== prevData.processDefinitionKey ||
      data.processDefinitionVersion !== prevData.processDefinitionVersion
    ) {
      this.loadVariables();
      this.loadFlowNodeNames();
    }
  }

  changeDefinition = async (key, version, tenants) => {
    const {groupBy, filter} = this.props.report.data;

    const change = {
      processDefinitionKey: {$set: key},
      processDefinitionVersion: {$set: version},
      tenantIds: {$set: tenants},
      parameters: {$set: {}},
      configuration: {
        excludedColumns: {$set: []},
        columnOrder: {
          $set: {
            inputVariables: [],
            instanceProps: [],
            outputVariables: [],
            variables: []
          }
        },
        heatmapTargetValue: {
          $set: {
            active: false,
            values: {}
          }
        },
        xml: {
          $set: key && version ? await loadProcessDefinitionXml(key, version, tenants[0]) : null
        }
      },
      filter: {$set: filter.filter(({type}) => type !== 'executedFlowNodes' && type !== 'variable')}
    };

    if (groupBy && groupBy.type === 'variable') {
      change.groupBy = {$set: null};
      change.visualization = {$set: null};
    }

    this.props.updateReport(change, true);
  };

  updateReport = (type, newValue) => {
    this.props.updateReport(processConfig.update(type, newValue, this.props), true);
  };

  render() {
    const {data} = this.props.report;
    return (
      <div className="ReportControlPanel">
        <ul>
          <li className="select">
            <span className="label">Process Definition</span>
            <DefinitionSelection
              type="process"
              definitionKey={data.processDefinitionKey}
              definitionVersion={data.processDefinitionVersion}
              tenants={data.tenantIds}
              xml={data.configuration.xml}
              onChange={this.changeDefinition}
              renderDiagram
              enableAllVersionSelection
            />
          </li>
          {['view', 'groupBy', 'visualization'].map((field, idx, fields) => {
            const previous = fields
              .filter((prev, prevIdx) => prevIdx < idx)
              .map(prev => data[prev]);

            return (
              <li className="select" key={field}>
                <span className="label">{formatters.convertCamelToSpaces(field)}</span>
                <ReportSelect
                  type="process"
                  field={field}
                  value={data[field]}
                  report={this.props.report}
                  variables={{variable: this.state.variables}}
                  previous={previous}
                  disabled={
                    !data.processDefinitionKey ||
                    !data.processDefinitionVersion ||
                    previous.some(entry => !entry)
                  }
                  onChange={newValue => this.updateReport(field, newValue)}
                />
              </li>
            );
          })}
          <li className="filter">
            <Filter
              flowNodeNames={this.state.flowNodeNames}
              data={data.filter}
              onChange={this.props.updateReport}
              processDefinitionKey={data.processDefinitionKey}
              processDefinitionVersion={data.processDefinitionVersion}
              xml={data.configuration.xml}
              instanceCount={
                this.props.report.result && this.props.report.result.processInstanceCount
              }
            />
          </li>
          {isDurationHeatmap(data) && (
            <li>
              <TargetValueComparison
                report={this.props.report}
                onChange={this.props.updateReport}
              />
            </li>
          )}
          <Configuration
            type={data.visualization}
            onChange={this.props.updateReport}
            report={this.props.report}
          />
          {isProcessInstanceDuration(data) && (
            <li>
              <ProcessPart
                flowNodeNames={this.state.flowNodeNames}
                xml={data.configuration.xml}
                processPart={data.parameters.processPart}
                update={newPart =>
                  this.props.updateReport({parameters: {processPart: {$set: newPart}}}, true)
                }
              />
            </li>
          )}
        </ul>
      </div>
    );
  }
}
