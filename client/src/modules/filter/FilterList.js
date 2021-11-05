/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ActionItem} from 'components';
import {t} from 'translation';

import {NodeListPreview, DateFilterPreview, VariablePreview} from './modals';
import AssigneeFilterPreview from './AssigneeFilterPreview';
import FlowNodeResolver from './FlowNodeResolver';

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
  'doesNotIncludeIncident',
];

const stateFilters = [...instanceStateFilters, ...flowNodeStateFilters, ...incidentFilters];

export default class FilterList extends React.Component {
  createOperator = (name) => <span> {name} </span>;

  getVariableName = (type, nameOrId, variableExists) => {
    if (isDecisionVariable(type) && this.props.variables?.[type]) {
      if (variableExists) {
        const {name, id} = this.props.variables[type].find(({id}) => id === nameOrId);
        return name || id;
      }
      return t('report.missingVariable');
    }

    return nameOrId;
  };

  appliedToSnippet = ({appliedTo}) => {
    if (!this.props.definitions || this.props.definitions.length <= 1 || !appliedTo) {
      return null;
    }

    if (appliedTo?.[0] === 'all') {
      return (
        <p className="appliedTo">
          {t('common.filter.list.appliedTo')}: {t('common.all').toLowerCase()}{' '}
          {t('common.process.label-plural')}
        </p>
      );
    }

    return (
      <p className="appliedTo">
        {t('common.filter.list.appliedTo')}: {appliedTo.length}{' '}
        {t('common.process.label' + (appliedTo.length > 1 ? '-plural' : ''))}
      </p>
    );
  };

  render() {
    const {definitions} = this.props;
    const list = [];

    for (let i = 0; i < this.props.data.length; i++) {
      const filter = this.props.data[i];
      if (
        filter.type === 'instanceStartDate' ||
        filter.type === 'instanceEndDate' ||
        filter.type === 'evaluationDateTime'
      ) {
        list.push(
          <li key={i} className="listItem">
            <ActionItem
              type={getFilterLevelText(filter.filterLevel)}
              onEdit={this.props.openEditFilterModal(filter)}
              onClick={(evt) => {
                evt.stopPropagation();
                this.props.deleteFilter(filter);
              }}
            >
              <DateFilterPreview filterType={filter.type} filter={filter.data} />
              {this.appliedToSnippet(filter)}
            </ActionItem>
          </li>
        );
      } else if (filter.type === 'flowNodeStartDate' || filter.type === 'flowNodeEndDate') {
        list.push(
          <li key={i} className="listItem">
            <FlowNodeResolver
              key={i}
              definition={this.props.definitions.find(
                ({identifier}) => identifier === filter.appliedTo[0]
              )}
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
                    onEdit={!warning ? this.props.openEditFilterModal(filter) : undefined}
                    onClick={(evt) => {
                      evt.stopPropagation();
                      this.props.deleteFilter(filter);
                    }}
                  >
                    <DateFilterPreview filterType={filter.type} filter={filter.data} />
                    <br />
                    {filter.data.flowNodeIds?.length > 0 && (
                      <>
                        <span>{t('common.for')} </span>
                        {filter.data.flowNodeIds.map((id) => (
                          <b key={id}>
                            {flowNodeNames?.[id] || id}
                            <br />
                          </b>
                        ))}
                      </>
                    )}
                    {this.appliedToSnippet(filter)}
                  </ActionItem>
                );
              }}
            />
          </li>
        );
      } else {
        if (filter.type.toLowerCase().includes('variable')) {
          const {name, type, data} = filter.data;
          const definitionIsValid = checkDefinition(definitions, filter.appliedTo[0]);
          const variableExists = checkVariableExistence(filter.type, name, this.props.variables);
          const variableName = this.getVariableName(filter.type, name, variableExists);

          let warning;
          if (!definitionIsValid) {
            warning = t('common.filter.list.invalidDefinition');
          } else if (!variableExists) {
            warning = t('report.nonExistingVariable');
          }

          list.push(
            <li key={i} className="listItem">
              <ActionItem
                type={getFilterLevelText(filter.filterLevel)}
                warning={warning}
                onEdit={!warning ? this.props.openEditFilterModal(filter) : undefined}
                onClick={(evt) => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
              >
                {type === 'Date' ? (
                  <DateFilterPreview
                    filterType="variable"
                    variableName={variableName}
                    filter={data}
                  />
                ) : (
                  <VariablePreview type={filter.type} variableName={variableName} filter={data} />
                )}
                {this.appliedToSnippet(filter)}
              </ActionItem>
            </li>
          );
        } else if (
          ['executedFlowNodes', 'executingFlowNodes', 'canceledFlowNodes'].includes(filter.type)
        ) {
          list.push(
            <FlowNodeResolver
              key={i}
              definition={this.props.definitions.find(
                ({identifier}) => identifier === filter.appliedTo[0]
              )}
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
                      onEdit={!warning ? this.props.openEditFilterModal(filter) : undefined}
                      onClick={(evt) => {
                        evt.stopPropagation();
                        this.props.deleteFilter(filter);
                      }}
                    >
                      {filter.filterLevel === 'view' ? (
                        <>
                          <span className="parameterName">
                            {t('common.filter.types.flowNodeSelection')}
                          </span>
                          <b className="filterText">
                            {selectedNodes.length} {t('common.filter.excludedFlowNodes')}
                          </b>
                        </>
                      ) : (
                        <NodeListPreview
                          nodes={selectedNodes}
                          operator={operator}
                          type={filter.type}
                        />
                      )}
                      {this.appliedToSnippet(filter)}
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
                onEdit={this.props.openEditFilterModal(filter)}
                onClick={(evt) => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
              >
                <span className="parameterName">{t('common.filter.types.instanceDuration')}</span>
                <span className="filterText">
                  {operator === '<' && this.createOperator(t('common.filter.list.operators.less'))}
                  {operator === '>' && this.createOperator(t('common.filter.list.operators.more'))}
                  <b>
                    {value.toString()}{' '}
                    {t(`common.unit.${unit.slice(0, -1)}.label${value !== 1 ? '-plural' : ''}`)}
                  </b>
                </span>
                {this.appliedToSnippet(filter)}
              </ActionItem>
            </li>
          );
        } else if (filter.type === 'flowNodeDuration') {
          list.push(
            <FlowNodeResolver
              key={i}
              definition={this.props.definitions.find(
                ({identifier}) => identifier === filter.appliedTo[0]
              )}
              render={(flowNodeNames) => {
                const filters = filter.data;
                const definitionIsValid = checkDefinition(definitions, filter.appliedTo[0]);
                const allFlowNodesExist = checkAllFlowNodesExist(
                  flowNodeNames,
                  Object.keys(filters)
                );

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
                              `common.unit.${unit.slice(0, -1)}.label${
                                value !== 1 ? '-plural' : ''
                              }`
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
                      onEdit={!warning ? this.props.openEditFilterModal(filter) : undefined}
                      onClick={(evt) => {
                        evt.stopPropagation();
                        this.props.deleteFilter(filter);
                      }}
                    >
                      <span className="parameterName">
                        {t('common.filter.types.flowNodeDuration')}
                      </span>
                      <span className="filterText">{filterValues}</span>
                      {this.appliedToSnippet(filter)}
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
                  this.props.deleteFilter(filter);
                }}
              >
                <span className="parameterName">{getStateFilterParameterName(filter)}</span>
                <span
                  className="filterText"
                  dangerouslySetInnerHTML={{__html: getStateFilterFilterText(filter)}}
                />
                {this.appliedToSnippet(filter)}
              </ActionItem>
            </li>
          );
        } else if (['assignee', 'candidateGroup'].includes(filter.type)) {
          const definitionIsValid = checkDefinition(definitions, filter.appliedTo[0]);
          list.push(
            <li key={i} className="listItem">
              <ActionItem
                type={getFilterLevelText(filter.filterLevel)}
                warning={!definitionIsValid && t('common.filter.list.invalidDefinition')}
                onEdit={definitionIsValid ? this.props.openEditFilterModal(filter) : undefined}
                onClick={(evt) => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
              >
                <AssigneeFilterPreview filter={filter} />
                {this.appliedToSnippet(filter)}
              </ActionItem>
            </li>
          );
        }
      }
    }

    return <ul className="FilterList">{list}</ul>;
  }
}

FilterList.defaultProps = {
  openEditFilterModal: () => {},
  deleteFilter: () => {},
};

function isDecisionVariable(type) {
  return ['inputVariable', 'outputVariable'].includes(type);
}

function checkVariableExistence(type, name, variables) {
  if (!variables || (isDecisionVariable(type) && !variables[type])) {
    return true;
  }

  if (isDecisionVariable(type)) {
    return variables[type].some((variable) => variable.id === name);
  }

  return variables.some((variable) => variable.name === name);
}

function checkAllFlowNodesExist(availableFlowNodeNames, flowNodeIds) {
  if (!availableFlowNodeNames) {
    return true;
  }
  const availableFlowNodesIds = Object.keys(availableFlowNodeNames);
  return flowNodeIds.every((id) => availableFlowNodesIds.includes(id));
}

function getFilterLevelText(filterLevel) {
  if (!filterLevel) {
    return t('common.filter.decisionFilter');
  }
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
