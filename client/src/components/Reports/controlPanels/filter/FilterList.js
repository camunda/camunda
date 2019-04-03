/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import {ActionItem} from 'components';
import {formatters} from 'services';

import './FilterList.scss';

export default class FilterList extends React.Component {
  createOperator = name => {
    return <span className="FilterList__operator"> {name} </span>;
  };

  getVariableName = (type, nameOrId) => {
    if (this.props.variables) {
      return this.props.variables[type].find(({id}) => id === nameOrId).name;
    }

    return nameOrId;
  };

  render() {
    const list = [];

    for (let i = 0; i < this.props.data.length; i++) {
      const filter = this.props.data[i];
      if (filter.type.includes('Date')) {
        const {type} = filter.data;

        if (type === 'fixed') {
          list.push(
            <li
              key={i}
              onClick={this.props.openEditFilterModal(filter)}
              className="FilterList__item"
            >
              <ActionItem
                onClick={evt => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
                className="FilterList__action-item"
              >
                <span className="FilterList__parameter-name">
                  {formatters.camelCaseToLabel(filter.type)}
                </span>
                {this.createOperator('is between')}
                <span className="FilterList__value">
                  {moment(filter.data.start).format('YYYY-MM-DD')}
                </span>
                {this.createOperator('and')}
                <span className="FilterList__value">
                  {moment(filter.data.end).format('YYYY-MM-DD')}
                </span>
              </ActionItem>
            </li>
          );
        } else {
          const {unit, value} = filter.data.start;

          list.push(
            <li
              key={i}
              onClick={this.props.openEditFilterModal(filter)}
              className="FilterList__item"
            >
              <ActionItem
                onClick={evt => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
                className="FilterList__action-item"
              >
                <span className="FilterList__parameter-name">
                  {formatters.camelCaseToLabel(filter.type)}
                </span>
                {this.createOperator('less than')}
                <span className="FilterList__value">
                  {value.toString()} {unit.slice(0, -1)}
                  {value > 1 && 's'}
                </span>
                {this.createOperator('ago')}
              </ActionItem>
            </li>
          );
        }
      } else {
        if (filter.type.toLowerCase().includes('variable')) {
          const {name, type, data} = filter.data;

          if (type === 'Date') {
            list.push(
              <li
                key={i}
                onClick={this.props.openEditFilterModal(filter)}
                className="FilterList__item"
              >
                <ActionItem
                  onClick={evt => {
                    evt.stopPropagation();
                    this.props.deleteFilter(filter);
                  }}
                  className="FilterList__action-item"
                >
                  <span className="FilterList__parameter-name">
                    {this.getVariableName(filter.type, name)}
                  </span>
                  {this.createOperator('is between')}
                  <span className="FilterList__value">
                    {moment(data.start).format('YYYY-MM-DD')}
                  </span>
                  {this.createOperator('and')}
                  <span className="FilterList__value">{moment(data.end).format('YYYY-MM-DD')}</span>
                </ActionItem>
              </li>
            );
          } else if (type === 'Boolean') {
            list.push(
              <li
                key={i}
                onClick={this.props.openEditFilterModal(filter)}
                className="FilterList__item"
              >
                <ActionItem
                  onClick={evt => {
                    evt.stopPropagation();
                    this.props.deleteFilter(filter);
                  }}
                  className="FilterList__action-item"
                >
                  <span className="FilterList__parameter-name">
                    {this.getVariableName(filter.type, name)}
                  </span>
                  {this.createOperator('is')}
                  <span className="FilterList__value">{data.value.toString()}</span>
                </ActionItem>
              </li>
            );
          } else {
            const {operator, values} = data;
            list.push(
              <li
                key={i}
                onClick={this.props.openEditFilterModal(filter)}
                className="FilterList__item"
              >
                <ActionItem
                  onClick={evt => {
                    evt.stopPropagation();
                    this.props.deleteFilter(filter);
                  }}
                  className="FilterList__action-item"
                >
                  <span className="FilterList__parameter-name">
                    {this.getVariableName(filter.type, name)}
                  </span>
                  {(operator === 'in' || operator === '=') && this.createOperator('is')}
                  {operator === 'not in' &&
                    (values.length === 1
                      ? this.createOperator('is not')
                      : this.createOperator('is neither'))}
                  {operator === '<' && this.createOperator('is less than')}
                  {operator === '>' && this.createOperator('is greater than')}
                  {values.map((value, idx) => {
                    return (
                      <span key={idx}>
                        <span className="FilterList__value">{value.toString()}</span>
                        {idx < values.length - 1 &&
                          (operator === 'not in'
                            ? this.createOperator('nor')
                            : this.createOperator('or'))}
                      </span>
                    );
                  })}
                </ActionItem>
              </li>
            );
          }
        } else if (filter.type === 'executedFlowNodes') {
          const {values, operator} = filter.data;
          const flowNodes = this.props.flowNodeNames;

          list.push(
            <li
              key={i}
              onClick={this.props.openEditFilterModal(filter)}
              className="FilterList__item"
            >
              <ActionItem
                onClick={evt => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
                className="FilterList__action-item"
              >
                <span className="FilterList__parameter-name">Executed Flow Node</span>
                {this.createOperator(
                  operator === 'in' ? 'is' : values.length > 1 ? 'is neither' : 'is not'
                )}
                {values.map((value, idx) => {
                  return (
                    <span key={idx}>
                      <span className="FilterList__value">
                        {flowNodes
                          ? flowNodes[value.toString()] || value.toString()
                          : value.toString()}
                      </span>
                      {idx < values.length - 1 &&
                        this.createOperator(operator === 'in' ? 'or' : 'nor')}
                    </span>
                  );
                })}
              </ActionItem>
            </li>
          );
        } else if (filter.type === 'processInstanceDuration') {
          const {unit, value, operator} = filter.data;

          list.push(
            <li
              key={i}
              onClick={this.props.openEditFilterModal(filter)}
              className="FilterList__item"
            >
              <ActionItem
                onClick={evt => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
                className="FilterList__action-item"
              >
                <span className="FilterList__parameter-name">Duration</span>
                {operator === '<' && this.createOperator('is less than')}
                {operator === '>' && this.createOperator('is more than')}
                <span className="FilterList__value">
                  {value.toString()} {unit.slice(0, -1)}
                  {value > 1 && 's'}
                </span>
              </ActionItem>
            </li>
          );
        } else if (filter.type === 'runningInstancesOnly') {
          list.push(
            <li key={i} className="FilterList__item FilterList__item--not-editable">
              <ActionItem
                onClick={evt => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
                className="FilterList__action-item"
              >
                <span className="FilterList__parameter-name">Running Process Instances Only</span>
              </ActionItem>
            </li>
          );
        } else if (filter.type === 'completedInstancesOnly') {
          list.push(
            <li key={i} className="FilterList__item FilterList__item--not-editable">
              <ActionItem
                onClick={evt => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
                className="FilterList__action-item"
              >
                <span className="FilterList__parameter-name">Completed Process Instances Only</span>
              </ActionItem>
            </li>
          );
        } else if (filter.type === 'canceledInstancesOnly') {
          list.push(
            <li key={i} className="FilterList__item FilterList__item--not-editable">
              <ActionItem
                onClick={evt => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
                className="FilterList__action-item"
              >
                <span className="FilterList__parameter-name">Canceled Process Instances Only</span>
              </ActionItem>
            </li>
          );
        } else if (filter.type === 'nonCanceledInstancesOnly') {
          list.push(
            <li key={i} className="FilterList__item FilterList__item--not-editable">
              <ActionItem
                onClick={evt => {
                  evt.stopPropagation();
                  this.props.deleteFilter(filter);
                }}
                className="FilterList__action-item"
              >
                <span className="FilterList__parameter-name">
                  Non Canceled Process Instances Only
                </span>
              </ActionItem>
            </li>
          );
        }
      }

      if (i < this.props.data.length - 1) {
        list.push(
          <li className="FilterList__itemConnector" key={'connector_' + i}>
            and
          </li>
        );
      }
    }

    return <ul className="FilterList">{list}</ul>;
  }
}
