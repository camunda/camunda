/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {DefinitionSelection, Select} from 'components';
import {Filter} from 'filter';
import {withErrorHandling} from 'HOC';
import {getFlowNodeNames, reportConfig, loadProcessDefinitionXml, loadVariables} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';

import DistributedBy from './DistributedBy';
import AggregationType from './AggregationType';
import UserTaskDurationTime from './UserTaskDurationTime';
import ReportSelect from './ReportSelect';
import {TargetValueComparison} from './targetValue';
import {ProcessPart} from './ProcessPart';
import {isDurationHeatmap, isProcessInstanceDuration} from './service';

import './ReportControlPanel.scss';

const {process: processConfig} = reportConfig;

export default withErrorHandling(
  class ReportControlPanel extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        variables: null,
        flowNodeNames: null,
      };
    }

    componentDidMount() {
      const data = this.props.report.data;
      this.loadVariables(data);
      this.loadFlowNodeNames(data);
    }

    loadFlowNodeNames = async ({processDefinitionKey, processDefinitionVersions, tenantIds}) => {
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

    loadVariables = ({processDefinitionKey, processDefinitionVersions, tenantIds}) => {
      if (processDefinitionKey && processDefinitionVersions && tenantIds) {
        return new Promise((resolve, reject) => {
          this.props.mightFail(
            loadVariables({processDefinitionKey, processDefinitionVersions, tenantIds}),
            (variables) => this.setState({variables}, resolve),
            (error) => reject(showError(error))
          );
        });
      }
    };

    variableExists = (varName) =>
      this.state.variables.some((variable) => variable.name === varName);

    getVariableConfig = () => {
      const {view, groupBy, distributedBy} = this.props.report.data;

      if (view?.entity === 'variable') {
        return {
          name: view.property.name,
          reset: (change) => {
            change.view = {$set: null};
            change.groupBy = {$set: null};
            change.visualization = {$set: null};
          },
        };
      } else if (groupBy?.type === 'variable') {
        return {
          name: groupBy.value.name,
          reset: (change) => {
            change.groupBy = {$set: null};
            change.visualization = {$set: null};
          },
        };
      } else if (distributedBy?.type === 'variable') {
        return {
          name: distributedBy.value.name,
          reset: (change) => {
            change.distributedBy = {$set: {type: 'none', value: null}};
          },
        };
      }
    };

    changeDefinition = async ({key, versions, tenantIds, name}) => {
      const change = {
        processDefinitionKey: {$set: key},
        processDefinitionName: {$set: name},
        processDefinitionVersions: {$set: versions},
        tenantIds: {$set: tenantIds},
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
      };

      const definitionData = {
        processDefinitionKey: key,
        processDefinitionVersions: versions,
        tenantIds,
      };

      const variableConfig = this.getVariableConfig();
      if (variableConfig) {
        this.props.setLoading(true);
        await this.loadVariables(definitionData);
        this.props.setLoading(false);
        if (!this.variableExists(variableConfig.name)) {
          variableConfig.reset(change);
        }
      } else {
        this.loadVariables(definitionData);
      }

      this.loadFlowNodeNames(definitionData);
      this.props.updateReport(change, true);
    };

    updateReport = (type, newValue) => {
      this.props.updateReport(processConfig.update(type, newValue, this.props), true);
    };

    render() {
      const {data, result} = this.props.report;

      const shouldDisplayMeasure = ['frequency', 'duration'].includes(data.view?.property);

      return (
        <div className="ReportControlPanel">
          <div className="select source">
            <h3 className="sectionTitle">{t('common.dataSource')}</h3>
            <DefinitionSelection
              type="process"
              definitionKey={data.processDefinitionKey}
              versions={data.processDefinitionVersions}
              tenants={data.tenantIds}
              xml={data.configuration.xml}
              onChange={this.changeDefinition}
              renderDiagram
            />
          </div>
          <div className="scrollable">
            <div className="reportSetup">
              <h3 className="sectionTitle">{t('report.reportSetup')}</h3>
              <ul>
                <li className="select">
                  <span className="label">{t(`report.view.label`)}</span>
                  <ReportSelect
                    type="process"
                    field="view"
                    value={data.view}
                    report={this.props.report}
                    variables={{variable: this.state.variables}}
                    disabled={!data.processDefinitionKey}
                    onChange={(newValue) => this.updateReport('view', newValue)}
                  />
                </li>
                {shouldDisplayMeasure && (
                  <li className="select">
                    <span className="label">{t('report.measure')}</span>
                    <Select
                      value={data.view.property}
                      onChange={(property) => this.updateReport('view', {...data.view, property})}
                    >
                      <Select.Option value="frequency">{t('report.view.count')}</Select.Option>
                      <Select.Option value="duration">
                        {data.view.entity === 'incident'
                          ? t('report.view.resolutionDuration')
                          : t('report.view.duration')}
                      </Select.Option>
                    </Select>
                  </li>
                )}
                <li className="select">
                  <span className="label">{t(`report.groupBy.label`)}</span>
                  <ReportSelect
                    type="process"
                    field="groupBy"
                    value={data.groupBy}
                    report={this.props.report}
                    variables={{variable: this.state.variables}}
                    disabled={!data.processDefinitionKey || !data.view}
                    onChange={(newValue) => this.updateReport('groupBy', newValue)}
                    previous={[data.view]}
                  />
                </li>
                <AggregationType report={this.props.report} onChange={this.props.updateReport} />
                <UserTaskDurationTime
                  report={this.props.report}
                  onChange={this.props.updateReport}
                />
                <DistributedBy report={this.props.report} onChange={this.props.updateReport} />
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
            <div className="filter">
              <h3 className="sectionTitle">
                {t('common.filter.label')}
                {data.filter?.length > 0 && (
                  <span className="filterCount">{data.filter.length}</span>
                )}
              </h3>
              <Filter
                flowNodeNames={this.state.flowNodeNames}
                data={data.filter}
                onChange={this.props.updateReport}
                processDefinitionKey={data.processDefinitionKey}
                processDefinitionVersions={data.processDefinitionVersions}
                tenantIds={data.tenantIds}
                xml={data.configuration.xml}
                variables={this.state.variables}
              />
            </div>
            {result && typeof result.instanceCount !== 'undefined' && (
              <div className="instanceCount">
                {t(
                  `report.instanceCount.process.label${
                    result.instanceCount !== 1 ? '-plural' : ''
                  }`,
                  {count: result.instanceCount}
                )}
              </div>
            )}
          </div>
        </div>
      );
    }
  }
);
