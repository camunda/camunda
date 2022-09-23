/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

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
    const {data, definitions, deleteFilter, openEditFilterModal, variables} = this.props;
    const list = [];

    for (let i = 0; i < data.length; i++) {
      const filter = data[i];
      if (
        filter.type === 'instanceStartDate' ||
        filter.type === 'instanceEndDate' ||
        filter.type === 'evaluationDateTime'
      ) {
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

        const variablesLoaded = isDecisionVariable(filter.type)
          ? variables?.[filter.type]
          : variables;

        const areVariablesMissing =
          variablesLoaded &&
          !filterVariables.every(({name, type}) =>
            variableExists(name, type, filter.type, variables)
          );

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
                  : getVariableLabel(name, type, filter.type, variables);

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
              <span className="parameterName">
                {t('common.filter.types.processInstanceDuration')}
              </span>
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
                    <span className="parameterName">
                      {t('common.filter.types.flowNodeDuration')}
                    </span>
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
              <span className="parameterName">{getStateFilterParameterName(filter)}</span>
              <span
                className="filterText"
                dangerouslySetInnerHTML={{__html: getStateFilterFilterText(filter)}}
              />
              <AppliedToInfo filter={filter} definitions={definitions} />
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

FilterList.defaultProps = {
  openEditFilterModal: () => {},
  deleteFilter: () => {},
};

function isDecisionVariable(type) {
  return ['inputVariable', 'outputVariable'].includes(type);
}

function getVariableLabel(nameOrId, type, filterType, variables) {
  return isDecisionVariable(filterType)
    ? getDecisionVariableLabel(nameOrId, filterType, variables)
    : getProcessVariableLabel(nameOrId, type, variables);
}

function variableExists(nameOrId, type, filterType, variables) {
  return isDecisionVariable(filterType)
    ? decisionVariableExists(nameOrId, filterType, variables)
    : processVariablesExists(nameOrId, type, variables);
}

function getProcessVariableLabel(name, type, variables) {
  const {label} =
    variables?.find((variable) => variable.name === name && variable.type === type) || {};

  return label || name;
}

function getDecisionVariableLabel(id, type, variables) {
  const {name} = variables?.[type]?.find((variable) => variable.id === id) || {};
  return name || id;
}

function decisionVariableExists(variableId, filterType, variables) {
  return variables?.[filterType]?.some((variable) => variable.id === variableId);
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
