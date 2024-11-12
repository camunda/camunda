/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect, useCallback} from 'react';
import update from 'immutability-helper';
import deepEqual from 'fast-deep-equal';
import {Accordion, AccordionItem, DefinitionTooltip, Layer, Tag} from '@carbon/react';
import {Db2Database, Factor, Filter as FilterIcon} from '@carbon/icons-react';

import {Filter} from 'filter';
import {useDocs, useErrorHandling} from 'hooks';
import {
  getFlowNodeNames,
  loadProcessDefinitionXml,
  loadVariables as loadVariablesService,
  getRandomId,
} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';
import {setVariables as setVariablesService} from 'variables';

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

export default function ReportControlPanel({report, updateReport, setLoading}) {
  const [variables, setVariables] = useState(null);
  const [flowNodeNames, setFlowNodeNames] = useState(null);
  const {mightFail} = useErrorHandling();
  const {generateDocsLink} = useDocs();

  const loadFlowNodeNames = useCallback(
    ({key, versions, tenantIds}) => {
      if (key && versions && tenantIds) {
        return new Promise((resolve, reject) => {
          mightFail(
            getFlowNodeNames(key, versions[0], tenantIds[0]),
            (flowNodeNames) => {
              setFlowNodeNames(flowNodeNames);
              resolve();
            },
            (error) => reject(showError(error))
          );
        });
      }
    },
    [mightFail]
  );

  const loadVariables = useCallback(
    (definitions) => {
      return new Promise((resolve, reject) => {
        mightFail(
          loadVariablesService({
            processesToQuery: definitions.map(({key, versions, tenantIds}) => ({
              processDefinitionKey: key,
              processDefinitionVersions: versions,
              tenantIds,
            })),
            filter: report.data.filter,
          }),
          (variables) => {
            setVariables(variables);
            setVariablesService(variables);
            resolve();
          },
          (error) => reject(showError(error))
        );
      });
    },
    [report.data.filter, mightFail]
  );

  useEffect(() => {
    const {data} = report;

    if (data?.definitions?.length) {
      loadVariables(data.definitions);
      loadFlowNodeNames(data.definitions[0]);
    }
  }, [report, loadFlowNodeNames, loadVariables]);

  function loadXml({key, versions, tenantIds}) {
    if (key && versions?.[0] && tenantIds) {
      return new Promise((resolve, reject) => {
        mightFail(loadProcessDefinitionXml(key, versions[0], tenantIds[0]), resolve, (error) =>
          reject(showError(error))
        );
      });
    }

    return null;
  }

  function variableExists(varName) {
    return variables.some((variable) => variable.name === varName);
  }

  function getNewVariables(columns) {
    return variables.map((col) => 'variable:' + col.name).filter((col) => !columns.includes(col));
  }

  function getVariableConfig() {
    const {view, groupBy, distributedBy} = report.data;

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
  }

  async function copyDefinition(idx) {
    const {data} = report;
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

    setLoading(true);
    await updateReport(change, true);
    setLoading(false);
  }

  async function addDefinition(newDefinitions) {
    let change = {definitions: {$push: newDefinitions}};

    setLoading(true);
    const data = report.data;

    const {definitions} = update(data, change);
    change = {...change, ...(await processDefinitionUpdate(definitions))};
    change.configuration = change.configuration || {};
    if (data.definitions.length === 1) {
      // if we add the second definition, we need to make sure that it's not a heatmap report
      if (data.visualization === 'heat') {
        change.visualization = {$set: 'table'};
      }
    }

    await updateReport(change, true);
    setLoading(false);
  }

  async function removeDefinition(idx) {
    let change = {
      definitions: {$splice: [[idx, 1]]},
    };

    setLoading(true);
    const data = report.data;

    const {definitions} = update(data, change);
    change = {...change, ...(await processDefinitionUpdate(definitions))};

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

    await updateReport(change, true);
    setLoading(false);
  }

  async function changeDefinition(changedDefinition, idx) {
    setLoading(true);

    let change = {
      definitions: {
        [idx]: {$set: changedDefinition},
      },
    };

    const {definitions} = update(report.data, change);
    change = {...change, ...(await processDefinitionUpdate(definitions))};

    await updateReport(change, true);
    setLoading(false);
  }

  async function processDefinitionUpdate(newDefinitions) {
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
    } = report.data;

    const targetFlowNodes = Object.keys(values);
    const change = {};

    const [xml] = await Promise.all([
      loadXml(newDefinitions[0]),
      loadVariables(newDefinitions),
      loadFlowNodeNames(newDefinitions[0]),
    ]);

    change.configuration = {
      xml: {$set: xml},
      // disable bucket size config on definition update
      // reason: every definition has different data and needs a different bucket size
      customBucket: {active: {$set: false}},
      distributeByCustomBucket: {active: {$set: false}},
    };

    const variableConfig = getVariableConfig();
    if (variableConfig && !variableExists(variableConfig.name)) {
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
          $set: includedColumns.concat(getNewVariables(includedColumns.concat(excludedColumns))),
        },
      };
    }

    if (processPart && !checkAllFlowNodesExist(flowNodeNames, Object.values(processPart))) {
      change.configuration.processPart = {$set: null};
    }

    if (targetFlowNodes.length && !checkAllFlowNodesExist(flowNodeNames, targetFlowNodes)) {
      change.configuration.heatmapTargetValue = {$set: {active: false, values: {}}};
    }

    // disable isKpi when adding more than one definition since it is not supported
    if (newDefinitions.length > 1 && targetValue?.isKpi) {
      change.configuration.targetValue = {isKpi: {$set: false}};
    }

    return change;
  }

  const {data, result} = report;
  const shouldDisplayMeasure = ['frequency', 'duration', 'percentage'].includes(
    data.view?.properties[0]
  );

  return (
    <div className="ReportControlPanel">
      <Layer className="controlSections">
        <Accordion>
          <AccordionItem
            title={
              <>
                <DefinitionTooltip
                  openOnHover
                  definition={t('report.copyTooltip', {
                    entity: t('common.process.label'),
                    docsLink: generateDocsLink(
                      'components/userguide/additional-features/process-variants-comparison/'
                    ),
                  })}
                >
                  <Db2Database />
                  {t('common.dataSource')}
                </DefinitionTooltip>
                <AddDefinition
                  type="process"
                  definitions={data.definitions}
                  onAdd={addDefinition}
                />
              </>
            }
            open
          >
            <DefinitionList
              filters={data.filter}
              type="process"
              definitions={data.definitions}
              onCopy={copyDefinition}
              onChange={changeDefinition}
              onRemove={removeDefinition}
            />
          </AccordionItem>
          <AccordionItem
            title={
              <>
                <Factor />
                {t('report.reportSetup')}
              </>
            }
            open
          >
            <ul className="reportSetup">
              <li className="select">
                <span className="label">{t(`report.view.label`)}</span>
                <View
                  type="process"
                  report={report.data}
                  onChange={(change) => updateReport(change, true)}
                  variables={variables}
                />
                {data.view?.entity === 'variable' && (
                  <AggregationType report={data} onChange={updateReport} />
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
                  <TargetValueComparison report={report} onChange={updateReport} />
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
                      updateReport(change, true);
                    }}
                  />
                </li>
              )}
            </ul>
          </AccordionItem>
          <AccordionItem
            title={
              <>
                <FilterIcon />
                {t('common.filter.label')}
                {data.filter?.length > 0 && (
                  <Tag type="high-contrast" className="filterCount">
                    {data.filter.length}
                  </Tag>
                )}
              </>
            }
          >
            <Filter
              data={data.filter}
              onChange={updateReport}
              definitions={data.definitions}
              variables={variables}
            />
          </AccordionItem>
        </Accordion>
      </Layer>
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
