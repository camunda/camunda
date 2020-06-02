/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ActionItem} from 'components';
import {NodeListPreview, DateFilterPreview} from './modals';

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

          if (type === 'Date') {
            list.push(
              <li key={i} onClick={this.props.openEditFilterModal(filter)} className="listItem">
                <ActionItem
                  onClick={(evt) => {
                    evt.stopPropagation();
                    this.props.deleteFilter(filter);
                  }}
                >
                  <DateFilterPreview
                    filterType="variable"
                    variableName={this.getVariableName(filter.type, name)}
                    filter={filter.data.data}
                  />
                </ActionItem>
              </li>
            );
          } else if (type === 'Boolean') {
            const {values} = data;
            list.push(
              <li key={i} onClick={this.props.openEditFilterModal(filter)} className="listItem">
                <ActionItem
                  onClick={(evt) => {
                    evt.stopPropagation();
                    this.props.deleteFilter(filter);
                  }}
                >
                  <span className="parameterName">{this.getVariableName(filter.type, name)}</span>
                  {this.createOperator(t('common.filter.list.operators.is'))}
                  {values.map((value, idx) => {
                    return (
                      <span key={idx}>
                        {value === null ? (
                          <>
                            <span className="previewItemValue">{t('common.null')}</span>
                            {this.createOperator(t('common.filter.list.operators.or'))}
                            <span className="previewItemValue">{t('common.undefined')}</span>
                          </>
                        ) : (
                          <span className="previewItemValue">{value.toString()}</span>
                        )}
                        {idx < values.length - 1 &&
                          this.createOperator(t('common.filter.list.operators.or'))}
                      </span>
                    );
                  })}
                </ActionItem>
              </li>
            );
          } else {
            const {operator, values} = data;
            list.push(
              <li key={i} onClick={this.props.openEditFilterModal(filter)} className="listItem">
                <ActionItem
                  onClick={(evt) => {
                    evt.stopPropagation();
                    this.props.deleteFilter(filter);
                  }}
                >
                  <span className="parameterName">{this.getVariableName(filter.type, name)}</span>
                  {(operator === 'in' || operator === '=') &&
                    this.createOperator(t('common.filter.list.operators.is'))}
                  {operator === 'not in' &&
                    (values.length === 1
                      ? this.createOperator(t('common.filter.list.operators.not'))
                      : this.createOperator(t('common.filter.list.operators.neither')))}
                  {operator === '<' && this.createOperator(t('common.filter.list.operators.less'))}
                  {operator === '>' &&
                    this.createOperator(t('common.filter.list.operators.greater'))}
                  {values.map((value, idx) => {
                    return (
                      <span key={idx}>
                        <span className="previewItemValue">{value.toString()}</span>
                        {idx < values.length - 1 &&
                          (operator === 'not in'
                            ? this.createOperator(t('common.filter.list.operators.nor'))
                            : this.createOperator(t('common.filter.list.operators.or')))}
                      </span>
                    );
                  })}
                </ActionItem>
              </li>
            );
          }
        } else if (['executedFlowNodes', 'executingFlowNodes'].includes(filter.type)) {
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
                <NodeListPreview nodes={selectedNodes} operator={operator} />
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
                <span className="previewItemValue">
                  {value.toString()}{' '}
                  {t(`common.unit.${unit.slice(0, -1)}.label${value !== 1 ? '-plural' : ''}`)}
                </span>
              </ActionItem>
            </li>
          );
        } else if (filter.type === 'flowNodeDuration') {
          const filters = filter.data;
          const filtersCount = Object.keys(filters).length;
          const flowNodeNames = this.props.flowNodeNames || {};

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
                <span className="previewItemValue">
                  {filtersCount} {t(`common.flowNode.label${filtersCount !== 1 ? '-plural' : ''}`)}
                  <div className="Tooltip light">
                    <div className="Tooltip__text-bottom">
                      {Object.keys(filters).map((key, i) => {
                        const {value, unit, operator} = filters[key];
                        return (
                          <div key={key}>
                            <div key={key} className="flowNode">
                              <span className="previewItemValue">{flowNodeNames[key] || key}</span>
                              {operator === '<' &&
                                this.createOperator(t('common.filter.list.operators.less'))}
                              {operator === '>' &&
                                this.createOperator(t('common.filter.list.operators.more'))}
                              <span className="previewItemValue">
                                {value.toString()}{' '}
                                {t(
                                  `common.unit.${unit.slice(0, -1)}.label${
                                    value !== 1 ? '-plural' : ''
                                  }`
                                )}
                              </span>
                            </div>
                            {i !== filtersCount - 1 && t('common.filter.list.operators.or')}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </span>
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
                    <span className="previewItemValue">
                      {val === null ? t('common.filter.assigneeModal.unassigned') : val}
                    </span>
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

      if (i < this.props.data.length - 1) {
        list.push(
          <li className="listItemConnector" key={'connector_' + i}>
            {t('common.and')}
          </li>
        );
      }
    }

    return <ul className="FilterList">{list}</ul>;
  }
}

FilterList.defaultProps = {
  flowNodeNames: {},
};
