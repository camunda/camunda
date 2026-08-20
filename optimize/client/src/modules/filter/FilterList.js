/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Tag} from '@carbon/react';

import {ActionItem} from 'components';
import {t} from 'translation';

import {NodeListPreview, DateFilterPreview, VariablePreview} from './modals';
import AssigneeFilterPreview from './AssigneeFilterPreview';
import FlowNodeResolver from './FlowNodeResolver';
import AppliedToInfo from './AppliedToInfo';

import './FilterList.scss';

const instanceStateFilters = [
  'runningInstancesOnly',
  'completedInstancesOnly',
  'canceledInstancesOnly',
  'nonCanceledInstancesOnly',
  'suspendedInstancesOnly',
  'nonSuspendedInstancesOnly',
];

const flowNodeStateFilters = [
  'runningFlowNodesOnly',
  'completedFlowNodesOnly',
  'canceledFlowNodesOnly',
  'completedOrCanceledFlowNodesOnly',
];

const incidentFilters = [
  'includesOpenIncident',
  'includesResolvedIncident',
  'includesClosedIncident',
  'doesNotIncludeIncident',
];

const stateFilters = [...instanceStateFilters, ...flowNodeStateFilters, ...incidentFilters];

export default class FilterList extends React.Component {
  createOperator = (name) => <span> {name} </span>;

  render() {
    const {
      data,
      definitions,
      deleteFilter = () => {},
      openEditFilterModal = () => {},
      variables,
    } = this.props;
    const list = [];

    for (let i = 0; i < data.length; i++) {
      const filter = data[i];
      if (filter.type === 'instanceStartDate' || filter.type === 'instanceEndDate') {
        list.push(
          <li key={i} className="listItem">
            <ActionItem
              type={getFilterLevelText(filter.filterLevel)}
              onEdit={openEditFilterModal(filter)}
              onClick={(evt) => {
                evt.stopPropagation();
                deleteFilter(filter);
              }}
            >
              <DateFilterPreview filterType={filter.type} filter={filter.data} />
              <AppliedToInfo filter={filter} definitions={definitions} />
              <span className="note">* {t('common.filter.list.totalInstanceWarning')}</span>
            </ActionItem>
          </li>
        );
      } else if (filter.type === 'flowNodeStartDate' || filter.type === 'flowNodeEndDate') {
        list.push(
          <li key={i} className="listItem">
            <FlowNodeResolver
              key={i}
              definition={definitions.find(({identifier}) => identifier === filter.appliedTo[0])}
              render={(flowNodeNames) => {
                const allFlowNodesExist =
                  filter.data.flowNodeIds &&
                  checkAllFlowNodesExist(flowNodeNames, filter.data.flowNodeIds);
                const definitionIsValid = checkDefinition(definitions, filter.appliedTo[0]);

                let warning;
                if (filter.filterLevel === 'instance') {
                  if (!definitionIsValid) {
                    warning = t('common.filter.list.invalidDefinition');
                  } else if (!allFlowNodesExist) {
                    warning = t('report.nonExistingFlowNode');
                  }
                }

                return (
                  <ActionItem
                    type={getFilterLevelText(filter.filterLevel)}
                    warning={warning}
                    onEdit={openEditFilterModal(filter)}
                    onClick={(evt) => {
                      evt.stopPropagation();
                      deleteFilter(filter);
                    }}
                  >
                    <DateFilterPreview filterType={filter.type} filter={filter.data} />
                    {filter.data.flowNodeIds?.length > 0 && (
                      <>
                        <span className="flowNodes">
                          {t('common.for')}
                          {filter.data.flowNodeIds.map((id) => (
                            <b key={id}>
                              {flowNodeNames?.[id] || id}
                              <br />
                            </b>
                          ))}
                        </span>
                      </>
                    )}
                    <AppliedToInfo filter={filter} definitions={definitions} />
                    <span className="note">* {t('common.filter.list.totalInstanceWarning')}</span>
                  </ActionItem>
                );
              }}
            />
          </li>
        );
      } else if (filter.type.toLowerCase().includes('variable')) {
        const definitionIsValid = checkDefinition(definitions, filter.appliedTo[0]);
        const filterVariables =
          filter.type === 'multipleVariable' ? filter.data?.data : [filter.data];

        const areVariablesMissing =
          variables &&
          !filterVariables.every(({name, type}) => processVariablesExists(name, type, variables));

        let warning;
        if (!definitionIsValid) {
          warning = t('common.filter.list.invalidDefinition');
        } else if (areVariablesMissing) {
          warning = t('report.nonExistingVariable');
        }
        list.push(
          <li key={i} className="listItem">
            <ActionItem
              type={getFilterLevelText(filter.filterLevel)}
              warning={warning}
              onEdit={openEditFilterModal(filter)}
              onClick={(evt) => {
                evt.stopPropagation();
                deleteFilter(filter);
              }}
            >
              {filterVariables.map(({name, type, data}, idx) => {
                const variableLabel = areVariablesMissing
                  ? t('report.missingVariable')
                  : getProcessVariableLabel(name, type, variables);

                return (
                  <div key={idx}>
                    {type === 'Date' ? (
                      <DateFilterPreview
                        filterType="variable"
                        variableName={variableLabel}
                        filter={data}
                      />
                    ) : (
                      <VariablePreview
                        type={filter.type}
                        variableName={variableLabel}
                        filter={data}
                      />
                    )}

                    {filterVariables[idx + 1] && (
                      <div className="OrOperator">{t('common.filter.variableModal.or')}</div>
                    )}
                  </div>
                );
              })}
              <AppliedToInfo filter={filter} definitions={definitions} />
            </ActionItem>
          </li>
        );
      } else if (
        ['executedFlowNodes', 'executingFlowNodes', 'canceledFlowNodes'].includes(filter.type)
      ) {
        list.push(
          <FlowNodeResolver
            key={i}
            definition={definitions.find(({identifier}) => identifier === filter.appliedTo[0])}
            render={(flowNodeNames) => {
              const {values, operator} = filter.data;

              const allFlowNodesExist = checkAllFlowNodesExist(flowNodeNames, values);
              const selectedNodes = values.map((id) => ({name: flowNodeNames?.[id], id}));
              const definitionIsValid = checkDefinition(definitions, filter.appliedTo[0]);

              let warning;
              if (!definitionIsValid) {
                warning = t('common.filter.list.invalidDefinition');
              } else if (!allFlowNodesExist) {
                warning = t('report.nonExistingFlowNode');
              }

              return (
                <li className="listItem">
                  <ActionItem
                    type={getFilterLevelText(filter.filterLevel)}
                    warning={warning}
                    onEdit={openEditFilterModal(filter)}
                    onClick={(evt) => {
                      evt.stopPropagation();
                      deleteFilter(filter);
                    }}
                  >
                    {filter.type === 'executedFlowNodes' && filter.filterLevel === 'view' ? (
                      <>
                        <Tag type="blue" className="parameterName">
                          {t('common.filter.types.flowNodeSelection')}
                        </Tag>
                        <b className="filterText">
                          {selectedNodes.length}{' '}
                          {operator === 'in'
                            ? t('common.filter.selectedFlowNodes')
                            : t('common.filter.excludedFlowNodes')}
                        </b>
                      </>
                    ) : (
                      <NodeListPreview
                        nodes={selectedNodes}
                        operator={operator}
                        type={filter.type}
                      />
                    )}
                    <AppliedToInfo filter={filter} definitions={definitions} />
                  </ActionItem>
                </li>
              );
            }}
          />
        );
      } else if (filter.type === 'processInstanceDuration') {
        const {unit, value, operator} = filter.data;

        list.push(
          <li key={i} className="listItem">
            <ActionItem
              type={getFilterLevelText(filter.filterLevel)}
              onEdit={openEditFilterModal(filter)}
              onClick={(evt) => {
                evt.stopPropagation();
                deleteFilter(filter);
              }}
            >
              <Tag type="blue" className="parameterName">
                {t('common.filter.types.processInstanceDuration')}
              </Tag>
              <span className="filterText">
                {operator === '<' && this.createOperator(t('common.filter.list.operators.less'))}
                {operator === '>' && this.createOperator(t('common.filter.list.operators.more'))}
                <b>
                  {value.toString()}{' '}
                  {t(`common.unit.${unit.slice(0, -1)}.label${value !== 1 ? '-plural' : ''}`)}
                </b>
              </span>
              <AppliedToInfo filter={filter} definitions={definitions} />
            </ActionItem>
          </li>
        );
      } else if (filter.type === 'flowNodeDuration') {
        list.push(
          <FlowNodeResolver
            key={i}
            definition={definitions.find(({identifier}) => identifier === filter.appliedTo[0])}
            render={(flowNodeNames) => {
              const filters = filter.data;
              const definitionIsValid = checkDefinition(definitions, filter.appliedTo[0]);
              const allFlowNodesExist = checkAllFlowNodesExist(flowNodeNames, Object.keys(filters));

              let warning;
              if (!definitionIsValid) {
                warning = t('common.filter.list.invalidDefinition');
              } else if (!allFlowNodesExist) {
                warning = t('report.nonExistingFlowNode');
              }

              const filterValues = (
                <div className="filterValues">
                  {Object.keys(filters).map((key, i) => {
                    const {value, unit, operator} = filters[key];
                    return (
                      <div key={key} className="flowNode">
                        {i !== 0 && <span>{t('common.filter.list.operators.or')} </span>}
                        <b>{flowNodeNames?.[key] || key}</b>
                        {operator === '<' &&
                          this.createOperator(t('common.filter.list.operators.less'))}
                        {operator === '>' &&
                          this.createOperator(t('common.filter.list.operators.more'))}
                        <b>
                          {value.toString()}{' '}
                          {t(
                            `common.unit.${unit.slice(0, -1)}.label${value !== 1 ? '-plural' : ''}`
                          )}
                        </b>
                      </div>
                    );
                  })}
                </div>
              );

              return (
                <li className="listItem">
                  <ActionItem
                    type={getFilterLevelText(filter.filterLevel)}
                    warning={warning}
                    onEdit={openEditFilterModal(filter)}
                    onClick={(evt) => {
                      evt.stopPropagation();
                      deleteFilter(filter);
                    }}
                  >
                    <Tag type="blue" className="parameterName">
                      {t('common.filter.types.flowNodeDuration')}
                    </Tag>
                    <span className="filterText">{filterValues}</span>
                    <AppliedToInfo filter={filter} definitions={definitions} />
                  </ActionItem>
                </li>
              );
            }}
          />
        );
      } else if (stateFilters.includes(filter.type)) {
        list.push(
          <li key={i} className="listItem">
            <ActionItem
              type={getFilterLevelText(filter.filterLevel)}
              onClick={(evt) => {
                evt.stopPropagation();
                deleteFilter(filter);
              }}
            >
              <Tag type="blue" className="parameterName">
                {getStateFilterParameterName(filter)}
              </Tag>
              <span className="filterText">{getStateFilterFilterText(filter)}</span>
              <AppliedToInfo filter={filter} definitions={definitions} />
            </ActionItem>
          </li>
        );
      } else if (['assignee'].includes(filter.type)) {
        const definitionIsValid = checkDefinition(definitions, filter.appliedTo[0]);
        list.push(
          <li key={i} className="listItem">
            <ActionItem
              type={getFilterLevelText(filter.filterLevel)}
              warning={!definitionIsValid && t('common.filter.list.invalidDefinition')}
              onEdit={openEditFilterModal(filter)}
              onClick={(evt) => {
                evt.stopPropagation();
                deleteFilter(filter);
              }}
            >
              <AssigneeFilterPreview filter={filter} />
              <AppliedToInfo filter={filter} definitions={definitions} />
            </ActionItem>
          </li>
        );
      }
    }

    return <ul className="FilterList">{list}</ul>;
  }
}

function getProcessVariableLabel(name, type, variables) {
  const {label} =
    variables?.find((variable) => variable.name === name && variable.type === type) || {};

  return label || name;
}

function processVariablesExists(name, type, variables) {
  return variables?.some((variable) => variable.name === name && variable.type === type);
}

function checkAllFlowNodesExist(availableFlowNodeNames, flowNodeIds) {
  if (!availableFlowNodeNames) {
    return true;
  }
  const availableFlowNodesIds = Object.keys(availableFlowNodeNames);
  return flowNodeIds.every((id) => availableFlowNodesIds.includes(id));
}

function getFilterLevelText(filterLevel) {
  if (filterLevel === 'instance') {
    return t('common.filter.instanceFilter');
  }

  return t('common.filter.flowNodeFilter');
}

function getStateFilterParameterName({type, filterLevel}) {
  if (instanceStateFilters.includes(type)) {
    return t('common.filter.types.instanceState');
  }
  if (flowNodeStateFilters.includes(type)) {
    return t('common.filter.types.flowNodeStatus');
  }
  if (incidentFilters.includes(type)) {
    if (filterLevel === 'instance') {
      return t('common.filter.types.processIncident');
    }
    return t('common.filter.types.viewIncident');
  }
}

function getStateFilterFilterText({type}) {
  if (instanceStateFilters.includes(type)) {
    return t('common.filter.state.instancesOnly', {
      type: t('common.filter.state.previewLabels.' + type),
    });
  }
  if (flowNodeStateFilters.includes(type)) {
    return t('common.filter.state.flowNodesOnly', {
      type: t('common.filter.state.previewLabels.' + type),
    });
  }
  if (incidentFilters.includes(type)) {
    return t('common.filter.state.incidents', {
      type: t('common.filter.state.previewLabels.' + type),
    });
  }
}

function checkDefinition(definitions, identifier) {
  if (identifier === 'all') {
    return true;
  }

  const definition = definitions?.find((definition) => definition.identifier === identifier);
  return definition?.versions.length && definition?.tenantIds.length;
}
