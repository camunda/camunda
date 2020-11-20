/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ActionItem, Tooltip} from 'components';

import {NodeListPreview, DateFilterPreview, VariablePreview} from './modals';
import PreviewItemValue from './PreviewItemValue';

import './FilterList.scss';

import {t} from 'translation';

const instanceFilters = [
  'runningInstancesOnly',
  'completedInstancesOnly',
  'canceledInstancesOnly',
  'nonCanceledInstancesOnly',
  'suspendedInstancesOnly',
  'nonSuspendedInstancesOnly',
];

export default class FilterList extends React.Component {
  createOperator = (name) => <span> {name} </span>;

  getVariableName = (type, nameOrId) => {
    if (this.props.variables && this.props.variables[type].length) {
      return this.props.variables[type].find(({id}) => id === nameOrId).name;
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
          const variableName = this.getVariableName(filter.type, name);

          list.push(
            <li key={i} onClick={this.props.openEditFilterModal(filter)} className="listItem">
              <ActionItem
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
          const selectedNodes = values.map((id) => ({name: flowNodeNames[id], id}));

          list.push(
            <li key={i} onClick={this.props.openEditFilterModal(filter)} className="listItem">
              <ActionItem
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
            <li key={i} onClick={this.props.openEditFilterModal(filter)} className="listItem">
              <ActionItem
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
                    <PreviewItemValue>
                      {filtersCount}{' '}
                      {t(`common.flowNode.label${filtersCount !== 1 ? '-plural' : ''}`)}
                    </PreviewItemValue>
                  </Tooltip>
                )}
              </ActionItem>
            </li>
          );
        } else if (instanceFilters.includes(filter.type)) {
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
          const {values, operator} = filter.data;
          list.push(
            <li key={i} onClick={this.props.openEditFilterModal(filter)} className="listItem">
              <ActionItem
                onClick={(evt) => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
              >
                <span className="parameterName">{t(`common.filter.types.${filter.type}`)}</span>
                {operator === 'in' && this.createOperator(t('common.filter.list.operators.is'))}
                {operator === 'not in' &&
                  (values.length === 1
                    ? this.createOperator(t('common.filter.list.operators.not'))
                    : this.createOperator(t('common.filter.list.operators.neither')))}
                {values.map((val, idx) => (
                  <span key={val}>
                    <PreviewItemValue>
                      {val === null ? t('common.filter.assigneeModal.unassigned') : val}
                    </PreviewItemValue>
                    {idx < values.length - 1 &&
                      (operator === 'not in'
                        ? this.createOperator(t('common.filter.list.operators.nor'))
                        : this.createOperator(t('common.filter.list.operators.or')))}
                  </span>
                ))}
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
