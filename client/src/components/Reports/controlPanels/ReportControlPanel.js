/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';
import update from 'immutability-helper';
import deepEqual from 'fast-deep-equal';

import {Icon, Button} from 'components';
import {Filter} from 'filter';
import {withErrorHandling} from 'HOC';
import {getFlowNodeNames, loadProcessDefinitionXml, loadVariables, getRandomId} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';
import {setVariables} from 'variables';

import DistributedBy from './DistributedBy';
import AggregationType from './AggregationType';
import View from './View';
import GroupBy from './GroupBy';
import Sorting from './Sorting';
import {TargetValueComparison} from './targetValue';
import {ProcessPart} from './ProcessPart';
import {DefinitionList} from './DefinitionList';
import Measure from './Measure';
import AddDefinition from './AddDefinition';
import {isDurationHeatmap, isProcessInstanceDuration} from './service';

import './ReportControlPanel.scss';

export default withErrorHandling(
  class ReportControlPanel extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        variables: null,
        flowNodeNames: null,
        showSource: true,
        showSetup: true,
        showFilter: false,
      };
    }

    componentDidMount() {
      const data = this.props.report.data;

      if (data?.definitions?.length) {
        this.loadVariables(data.definitions);
        this.loadFlowNodeNames(data.definitions[0]);
      }
    }

    loadFlowNodeNames = ({key, versions, tenantIds}) => {
      if (key && versions && tenantIds) {
        return new Promise((resolve, reject) => {
          this.props.mightFail(
            getFlowNodeNames(key, versions[0], tenantIds[0]),
            (flowNodeNames) => this.setState({flowNodeNames}, resolve),
            (error) => reject(showError(error))
          );
        });
      }
    };

    loadVariables = (definitions) => {
      return new Promise((resolve, reject) => {
        this.props.mightFail(
          loadVariables(
            definitions.map(({key, versions, tenantIds}) => ({
              processDefinitionKey: key,
              processDefinitionVersions: versions,
              tenantIds,
            }))
          ),
          (variables) => {
            this.setState({variables}, resolve);
            setVariables(variables);
          },
          (error) => reject(showError(error))
        );
      });
    };

    loadXml = ({key, versions, tenantIds}) => {
      if (key && versions?.[0] && tenantIds) {
        return new Promise((resolve, reject) => {
          this.props.mightFail(
            loadProcessDefinitionXml(key, versions[0], tenantIds[0]),
            resolve,
            (error) => reject(showError(error))
          );
        });
      }

      return null;
    };

    variableExists = (varName) =>
      this.state.variables.some((variable) => variable.name === varName);

    getNewVariables = (columns) =>
      this.state.variables
        .map((col) => 'variable:' + col.name)
        .filter((col) => !columns.includes(col));

    getVariableConfig = () => {
      const {view, groupBy, distributedBy} = this.props.report.data;

      if (view?.entity === 'variable') {
        return {
          name: view.properties[0].name,
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

    copyDefinition = async (idx) => {
      const {data} = this.props.report;
      const definitionToCopy = data.definitions[idx];
      const {tenantIds, versions, name, key, displayName} = definitionToCopy;
      const newDefinition = {
        ...definitionToCopy,
        tenantIds: [...tenantIds],
        versions: [...versions],
        displayName: (displayName || name || key) + ` (${t('common.copyLabel')})`,
        identifier: getRandomId(),
      };

      const change = {
        definitions: {$splice: [[idx, 0, newDefinition]]},
      };
      if (data.visualization === 'heat') {
        change.visualization = {$set: 'table'};
      }

      this.props.setLoading(true);
      await this.props.updateReport(change, true);
      this.props.setLoading(false);
    };

    addDefinition = async (newDefinitions) => {
      let change = {definitions: {$push: newDefinitions}};

      this.props.setLoading(true);
      const data = this.props.report.data;

      const {definitions} = update(data, change);
      change = {...change, ...(await this.processDefinitionUpdate(definitions))};
      change.configuration = change.configuration || {};
      if (data.definitions.length === 1) {
        // if we add the second definition, we need to make sure that it's not a heatmap report
        if (data.visualization === 'heat') {
          change.visualization = {$set: 'table'};
        }
      }

      await this.props.updateReport(change, true);
      this.props.setLoading(false);
    };

    removeDefinition = async (idx) => {
      let change = {
        definitions: {$splice: [[idx, 1]]},
      };

      this.props.setLoading(true);
      const data = this.props.report.data;

      const {definitions} = update(data, change);
      change = {...change, ...(await this.processDefinitionUpdate(definitions))};

      if (data.definitions.length === 1) {
        // removing the last definition will reset view and groupby options
        change = {
          ...change,
          view: {$set: null},
          groupBy: {$set: null},
          visualization: {$set: null},
          distributedBy: {$set: {type: 'none', value: null}},
        };
      }

      if (definitions.length === 1 && data.distributedBy.type === 'process') {
        // going back to single definition reports resets distributed by process reports
        change.distributedBy = {$set: {type: 'none', value: null}};
        if (data.groupBy.type === 'none') {
          change.visualization = {$set: 'number'};
        }
      }

      const newFilters = [];
      const identifierOfRemovedDefinition = data.definitions[idx].identifier;
      data.filter.forEach((filter) => {
        if (filter.appliedTo.includes(identifierOfRemovedDefinition)) {
          if (filter.appliedTo.length > 1) {
            // if the filter contains at least one other definition, we remove the removed definition from the list
            newFilters.push({
              ...filter,
              appliedTo: filter.appliedTo.filter(
                (identifier) => identifier !== identifierOfRemovedDefinition
              ),
            });
          }
        } else {
          newFilters.push(filter);
        }
      });
      change.filter = {$set: newFilters};

      await this.props.updateReport(change, true);
      this.props.setLoading(false);
    };

    changeDefinition = async (changedDefinition, idx) => {
      this.props.setLoading(true);

      let change = {
        definitions: {
          [idx]: {$set: changedDefinition},
        },
      };

      const {definitions} = update(this.props.report.data, change);
      change = {...change, ...(await this.processDefinitionUpdate(definitions))};

      await this.props.updateReport(change, true);
      this.props.setLoading(false);
    };

    processDefinitionUpdate = async (newDefinitions) => {
      if (!newDefinitions?.length) {
        return {};
      }

      const {
        configuration: {
          tableColumns: {columnOrder, includedColumns, excludedColumns},
          processPart,
          heatmapTargetValue: {values},
          targetValue,
        },
        definitions,
      } = this.props.report.data;

      const targetFlowNodes = Object.keys(values);
      const change = {};

      const [xml] = await Promise.all([
        this.loadXml(newDefinitions[0]),
        this.loadVariables(newDefinitions),
        this.loadFlowNodeNames(newDefinitions[0]),
      ]);

      change.configuration = {
        xml: {$set: xml},
        // disable bucket size config on definition update
        // reason: every definition has different data and needs a different bucket size
        customBucket: {active: {$set: false}},
        distributeByCustomBucket: {active: {$set: false}},
      };

      const variableConfig = this.getVariableConfig();
      if (variableConfig && !this.variableExists(variableConfig.name)) {
        variableConfig.reset(change);
      }

      if (columnOrder.length) {
        const previousDefinitionKeys = definitions.map(({key}) => key);
        const newDefinitionKeys = newDefinitions.map(({key}) => key);

        if (!deepEqual(previousDefinitionKeys, newDefinitionKeys)) {
          change.configuration.tableColumns = {columnOrder: {$set: []}};
        }
      }

      if (includedColumns.length) {
        change.configuration.tableColumns = {
          ...change.configuration.tableColumns,
          includedColumns: {
            $set: includedColumns.concat(
              this.getNewVariables(includedColumns.concat(excludedColumns))
            ),
          },
        };
      }

      if (
        processPart &&
        !checkAllFlowNodesExist(this.state.flowNodeNames, Object.values(processPart))
      ) {
        change.configuration.processPart = {$set: null};
      }

      if (
        targetFlowNodes.length &&
        !checkAllFlowNodesExist(this.state.flowNodeNames, targetFlowNodes)
      ) {
        change.configuration.heatmapTargetValue = {$set: {active: false, values: {}}};
      }

      // disable isKpi when adding more than one definition since it is not supported
      if (newDefinitions.length > 1 && targetValue?.isKpi) {
        change.configuration.targetValue = {isKpi: {$set: false}};
      }

      return change;
    };

    render() {
      const {report, updateReport} = this.props;
      const {data, result} = this.props.report;
      const {showSource, showSetup, showFilter, flowNodeNames, variables} = this.state;

      const shouldDisplayMeasure = ['frequency', 'duration', 'percentage'].includes(
        data.view?.properties[0]
      );

      return (
        <div className="ReportControlPanel">
          <div className="controlSections">
            <section className={classnames('select', 'source', {collapsed: !showSource})}>
              <div
                tabIndex="0"
                className="sectionTitle"
                onClick={() => {
                  this.setState({showSource: !showSource});
                }}
                onKeyDown={(evt) => {
                  if (
                    (evt.key === ' ' || evt.key === 'Enter') &&
                    evt.target === evt.currentTarget
                  ) {
                    this.setState({showSource: !showSource});
                  }
                }}
              >
                <Icon type="data-source" />
                {t('common.dataSource')}
                <AddDefinition
                  type="process"
                  definitions={data.definitions}
                  onAdd={this.addDefinition}
                />
                <span className={classnames('sectionToggle', {open: showSource})}>
                  <Icon type="down" />
                </span>
              </div>
              <DefinitionList
                type="process"
                definitions={data.definitions}
                onCopy={this.copyDefinition}
                onChange={this.changeDefinition}
                onRemove={this.removeDefinition}
              />
            </section>
            <section className={classnames('reportSetup', {collapsed: !showSetup})}>
              <Button
                className="sectionTitle"
                onClick={() => {
                  this.setState({showSetup: !showSetup});
                }}
              >
                <Icon type="report" />
                {t('report.reportSetup')}
                <span className={classnames('sectionToggle', {open: showSetup})}>
                  <Icon type="down" />
                </span>
              </Button>
              <ul>
                <li className="select">
                  <span className="label">{t(`report.view.label`)}</span>
                  <View
                    type="process"
                    report={report.data}
                    onChange={(change) => updateReport(change, true)}
                    variables={variables}
                  />
                  {data.view?.entity === 'variable' && (
                    <AggregationType report={data} onChange={this.props.updateReport} />
                  )}
                </li>
                {shouldDisplayMeasure && (
                  <Measure report={data} onChange={(change) => updateReport(change, true)} />
                )}
                <GroupBy
                  type="process"
                  report={report.data}
                  onChange={(change) => updateReport(change, true)}
                  variables={{variable: variables}}
                />
                <DistributedBy
                  report={report.data}
                  onChange={(change) => updateReport(change, true)}
                  variables={variables}
                />
                <Sorting
                  type="process"
                  report={report.data}
                  onChange={(change) => updateReport(change, true)}
                />
                {isDurationHeatmap(data) && (
                  <li className="select">
                    <span className="label">{t('report.heatTarget.label')}</span>
                    <TargetValueComparison
                      report={this.props.report}
                      onChange={this.props.updateReport}
                    />
                  </li>
                )}
                {isProcessInstanceDuration(data) && data.definitions?.length <= 1 && (
                  <li>
                    <ProcessPart
                      flowNodeNames={flowNodeNames}
                      xml={data.configuration.xml}
                      processPart={data.configuration.processPart}
                      update={(newPart) => {
                        const aggregations = data.configuration.aggregationTypes;
                        const change = {configuration: {processPart: {$set: newPart}}};
                        const isPercentile = (agg) => agg.type === 'percentile';
                        if (aggregations.find(isPercentile)) {
                          const newAggregations = aggregations.filter((agg) => !isPercentile(agg));
                          if (newAggregations.length === 0) {
                            newAggregations.push({type: 'avg', value: null});
                          }

                          change.configuration.aggregationTypes = {$set: newAggregations};
                        }
                        this.props.updateReport(change, true);
                      }}
                    />
                  </li>
                )}
              </ul>
            </section>
            <section className={classnames('filter', {collapsed: !showFilter})}>
              <Button
                className="sectionTitle"
                onClick={() => {
                  this.setState({showFilter: !showFilter});
                }}
              >
                <Icon type="filter" />
                {t('common.filter.label')}
                <span className={classnames('sectionToggle', {open: showFilter})}>
                  <Icon type="down" />
                </span>
                {data.filter?.length > 0 && (
                  <span className="filterCount">{data.filter.length}</span>
                )}
              </Button>
              <Filter
                data={data.filter}
                onChange={this.props.updateReport}
                definitions={data.definitions}
                variables={variables}
              />
            </section>
          </div>
          {result && typeof result.instanceCount !== 'undefined' && (
            <div className="instanceCount">
              {t(
                `report.instanceCount.process.label${
                  result.instanceCountWithoutFilters !== 1 ? '-plural' : ''
                }-withFilter`,
                {
                  count: result.instanceCount,
                  totalCount:
                    (haveDateFilter(data.filter) ? '*' : '') + result.instanceCountWithoutFilters,
                }
              )}
            </div>
          )}
        </div>
      );
    }
  }
);

function checkAllFlowNodesExist(availableFlowNodeNames, flowNodeIds) {
  if (!availableFlowNodeNames) {
    return true;
  }
  const availableFlowNodesIds = Object.keys(availableFlowNodeNames);
  return flowNodeIds.every((id) => availableFlowNodesIds.includes(id));
}

function haveDateFilter(filters) {
  return filters?.some((filter) => filter.type.toLowerCase().includes('date'));
}
