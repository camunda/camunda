/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {ActionItem, Tooltip} from 'components';
import {t} from 'translation';

import {NodeListPreview, DateFilterPreview, VariablePreview} from './modals';
import PreviewItemValue from './PreviewItemValue';
import AssigneeFilterPreview from './AssigneeFilterPreview';

import './FilterList.scss';

const stateFilters = [
  'runningInstancesOnly',
  'completedInstancesOnly',
  'canceledInstancesOnly',
  'nonCanceledInstancesOnly',
  'suspendedInstancesOnly',
  'nonSuspendedInstancesOnly',
  'includesOpenIncident',
  'includesResolvedIncident',
  'doesNotIncludeIncident',
  'runningFlowNodesOnly',
  'completedFlowNodesOnly',
  'canceledFlowNodesOnly',
  'completedOrCanceledFlowNodesOnly',
];

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

  render() {
    const list = [];

    for (let i = 0; i < this.props.data.length; i++) {
      const filter = this.props.data[i];
      if (filter.type.includes('Date')) {
        list.push(
          <li key={i} onClick={this.props.openEditFilterModal(filter)} className="listItem">
            <ActionItem
              onClick={(evt) => {
                evt.stopPropagation();
                this.props.deleteFilter(filter);
              }}
            >
              <DateFilterPreview filterType={filter.type} filter={filter.data} />
            </ActionItem>
          </li>
        );
      } else {
        if (filter.type.toLowerCase().includes('variable')) {
          const {name, type, data} = filter.data;
          const variableExists = checkVariableExistence(filter.type, name, this.props.variables);
          const variableName = this.getVariableName(filter.type, name, variableExists);

          list.push(
            <li
              key={i}
              onClick={variableExists ? this.props.openEditFilterModal(filter) : undefined}
              className={classnames('listItem', {notEditable: !variableExists})}
            >
              <ActionItem
                warning={!variableExists && t('report.nonExistingVariable')}
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
                  <VariablePreview variableName={variableName} filter={data} />
                )}
              </ActionItem>
            </li>
          );
        } else if (
          ['executedFlowNodes', 'executingFlowNodes', 'canceledFlowNodes'].includes(filter.type)
        ) {
          const {values, operator} = filter.data;
          const flowNodeNames = this.props.flowNodeNames || {};
          const allFlowNodesExist = checkAllFlowNodesExist(flowNodeNames, values);
          const selectedNodes = values.map((id) => ({name: flowNodeNames[id], id}));

          list.push(
            <li
              key={i}
              onClick={allFlowNodesExist ? this.props.openEditFilterModal(filter) : undefined}
              className={classnames('listItem', {notEditable: !allFlowNodesExist})}
            >
              <ActionItem
                warning={!allFlowNodesExist && t('report.nonExistingFlowNode')}
                onClick={(evt) => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
              >
                <NodeListPreview nodes={selectedNodes} operator={operator} type={filter.type} />
              </ActionItem>
            </li>
          );
        } else if (filter.type === 'processInstanceDuration') {
          const {unit, value, operator} = filter.data;

          list.push(
            <li key={i} onClick={this.props.openEditFilterModal(filter)} className="listItem">
              <ActionItem
                onClick={(evt) => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
              >
                <span className="parameterName">{t('common.filter.types.duration')}</span>
                {operator === '<' && this.createOperator(t('common.filter.list.operators.less'))}
                {operator === '>' && this.createOperator(t('common.filter.list.operators.more'))}
                <PreviewItemValue>
                  {value.toString()}{' '}
                  {t(`common.unit.${unit.slice(0, -1)}.label${value !== 1 ? '-plural' : ''}`)}
                </PreviewItemValue>
              </ActionItem>
            </li>
          );
        } else if (filter.type === 'flowNodeDuration') {
          const filters = filter.data;
          const filtersCount = Object.keys(filters).length;
          const flowNodeNames = this.props.flowNodeNames || {};
          const allFlowNodesExist = checkAllFlowNodesExist(flowNodeNames, Object.keys(filters));

          const filterValues = (
            <div className="filterValues">
              {Object.keys(filters).map((key, i) => {
                const {value, unit, operator} = filters[key];
                return (
                  <div key={key}>
                    <div key={key} className="flowNode">
                      <PreviewItemValue>{flowNodeNames[key] || key}</PreviewItemValue>
                      {operator === '<' &&
                        this.createOperator(t('common.filter.list.operators.less'))}
                      {operator === '>' &&
                        this.createOperator(t('common.filter.list.operators.more'))}
                      <PreviewItemValue>
                        {value.toString()}{' '}
                        {t(`common.unit.${unit.slice(0, -1)}.label${value !== 1 ? '-plural' : ''}`)}
                      </PreviewItemValue>
                    </div>
                    {i !== filtersCount - 1 && t('common.filter.list.operators.or')}
                  </div>
                );
              })}
            </div>
          );

          list.push(
            <li
              key={i}
              onClick={allFlowNodesExist ? this.props.openEditFilterModal(filter) : undefined}
              className={classnames('listItem', {notEditable: !allFlowNodesExist})}
            >
              <ActionItem
                warning={!allFlowNodesExist && t('report.nonExistingFlowNode')}
                onClick={(evt) => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
              >
                <span className="parameterName">{t('common.filter.types.duration')}</span>
                {this.createOperator(t('common.filter.durationModal.appliedTo'))}
                {this.props.expanded ? (
                  filterValues
                ) : (
                  <Tooltip position="bottom" content={filterValues}>
                    <span className="PreviewItemValue">
                      {filtersCount}{' '}
                      {t(`common.flowNode.label${filtersCount !== 1 ? '-plural' : ''}`)}
                    </span>
                  </Tooltip>
                )}
              </ActionItem>
            </li>
          );
        } else if (stateFilters.includes(filter.type)) {
          list.push(
            <li key={i} className="listItem notEditable">
              <ActionItem
                onClick={(evt) => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
              >
                <span className="parameterName">{t('common.filter.types.' + filter.type)}</span>
              </ActionItem>
            </li>
          );
        } else if (['assignee', 'candidateGroup'].includes(filter.type)) {
          list.push(
            <li key={i} onClick={this.props.openEditFilterModal(filter)} className="listItem">
              <ActionItem
                onClick={(evt) => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
              >
                <AssigneeFilterPreview filter={filter} />
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
  flowNodeNames: {},
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
  const availableFlowNodesIds = Object.keys(availableFlowNodeNames);
  return flowNodeIds.every((id) => availableFlowNodesIds.includes(id));
}
