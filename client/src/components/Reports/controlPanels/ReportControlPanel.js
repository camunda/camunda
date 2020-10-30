/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import equal from 'deep-equal';

import {DefinitionSelection} from 'components';
import {Filter} from 'filter';
import {withErrorHandling} from 'HOC';
import {getFlowNodeNames, reportConfig, loadProcessDefinitionXml, loadVariables} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';

import ReportSelect from './ReportSelect';
import {TargetValueComparison} from './targetValue';
import {ProcessPart} from './ProcessPart';
import {Configuration} from './Configuration';
import {isDurationHeatmap, isProcessInstanceDuration} from './service';

import './ReportControlPanel.scss';

const {process: processConfig} = reportConfig;

export default withErrorHandling(
  class ReportControlPanel extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        variables: [],
        flowNodeNames: null,
      };
    }

    componentDidMount() {
      this.loadVariables();
      this.loadFlowNodeNames();
    }

    loadFlowNodeNames = async () => {
      const {
        data: {processDefinitionKey, processDefinitionVersions, tenantIds},
      } = this.props.report;

      if (processDefinitionKey && processDefinitionVersions && tenantIds) {
        this.setState({
          flowNodeNames: await getFlowNodeNames(
            processDefinitionKey,
            processDefinitionVersions[0],
            tenantIds[0]
          ),
        });
      }
    };

    loadVariables = () => {
      const {processDefinitionKey, processDefinitionVersions, tenantIds} = this.props.report.data;
      if (processDefinitionKey && processDefinitionVersions && tenantIds) {
        this.props.mightFail(
          loadVariables({processDefinitionKey, processDefinitionVersions, tenantIds}),
          (variables) => this.setState({variables}),
          showError
        );
      }
    };

    componentDidUpdate(prevProps) {
      const {data} = this.props.report;
      const {data: prevData} = prevProps.report;

      if (
        data.processDefinitionKey !== prevData.processDefinitionKey ||
        !equal(data.processDefinitionVersions, prevData.processDefinitionVersions) ||
        !equal(data.tenantIds, prevData.tenantIds)
      ) {
        this.loadVariables();
        this.loadFlowNodeNames();
      }
    }

    changeDefinition = async ({key, versions, tenantIds, name}) => {
      const {view, groupBy, filter} = this.props.report.data;

      const change = {
        processDefinitionKey: {$set: key},
        processDefinitionName: {$set: name},
        processDefinitionVersions: {$set: versions},
        tenantIds: {$set: tenantIds},
        distributedBy: {
          $set: {type: 'none', value: null},
        },
        configuration: {
          tableColumns: {
            $set: {
              includeNewVariables: true,
              includedColumns: [],
              excludedColumns: [],
            },
          },
          columnOrder: {
            $set: {
              inputVariables: [],
              instanceProps: [],
              outputVariables: [],
              variables: [],
            },
          },
          heatmapTargetValue: {
            $set: {
              active: false,
              values: {},
            },
          },
          xml: {
            $set:
              key && versions && versions[0]
                ? await loadProcessDefinitionXml(key, versions[0], tenantIds[0])
                : null,
          },
          processPart: {$set: null},
        },
        filter: {
          $set: filter.filter(
            ({type}) =>
              ![
                'executedFlowNodes',
                'executingFlowNodes',
                'canceledFlowNodes',
                'variable',
                'assignee',
                'candidateGroup',
              ].includes(type)
          ),
        },
      };

      if (view?.entity === 'variable') {
        change.view = {$set: null};
        change.groupBy = {$set: null};
        change.visualization = {$set: null};
      }

      if (groupBy?.type === 'variable') {
        change.groupBy = {$set: null};
        change.visualization = {$set: null};
      }

      this.props.updateReport(change, true);
    };

    updateReport = (type, newValue) => {
      this.props.updateReport(processConfig.update(type, newValue, this.props), true);
    };

    render() {
      const {data, result} = this.props.report;

      return (
        <div className="ReportControlPanel">
          <ul>
            <li className="select">
              <span className="label">{t('report.definition.process')}</span>
              <DefinitionSelection
                type="process"
                definitionKey={data.processDefinitionKey}
                versions={data.processDefinitionVersions}
                tenants={data.tenantIds}
                xml={data.configuration.xml}
                onChange={this.changeDefinition}
                renderDiagram
              />
            </li>
            {['view', 'groupBy', 'visualization'].map((field, idx, fields) => {
              const previous = fields
                .filter((prev, prevIdx) => prevIdx < idx)
                .map((prev) => data[prev]);

              return (
                <li className="select" key={field}>
                  <span className="label">{t(`report.${field}.label`)}</span>
                  <ReportSelect
                    type="process"
                    field={field}
                    value={data[field]}
                    report={this.props.report}
                    variables={{variable: this.state.variables}}
                    previous={previous}
                    disabled={!data.processDefinitionKey || previous.some((entry) => !entry)}
                    onChange={(newValue) => this.updateReport(field, newValue)}
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
                processDefinitionVersions={data.processDefinitionVersions}
                tenantIds={data.tenantIds}
                xml={data.configuration.xml}
              />
            </li>
            {result && typeof result.instanceCount !== 'undefined' && (
              <li>
                {t(
                  `report.instanceCount.process.label${
                    result.instanceCount !== 1 ? '-plural' : ''
                  }`,
                  {count: result.instanceCount}
                )}
              </li>
            )}
            <Configuration
              type={data.visualization}
              onChange={this.props.updateReport}
              report={this.props.report}
            />
            {isDurationHeatmap(data) && (
              <li>
                <TargetValueComparison
                  report={this.props.report}
                  onChange={this.props.updateReport}
                />
              </li>
            )}
            {isProcessInstanceDuration(data) && (
              <li>
                <ProcessPart
                  flowNodeNames={this.state.flowNodeNames}
                  xml={data.configuration.xml}
                  processPart={data.configuration.processPart}
                  update={(newPart) => {
                    const change = {configuration: {processPart: {$set: newPart}}};
                    if (data.configuration.aggregationType === 'median') {
                      change.configuration.aggregationType = {$set: 'avg'};
                    }
                    this.props.updateReport(change, true);
                  }}
                />
              </li>
            )}
          </ul>
        </div>
      );
    }
  }
);
