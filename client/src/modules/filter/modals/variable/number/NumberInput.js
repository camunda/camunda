/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ButtonGroup, Button, Input, LabeledInput} from 'components';
import update from 'immutability-helper';

import './NumberInput.scss';
import {t} from 'translation';

export default class NumberInput extends React.Component {
  static defaultFilter = {operator: 'in', values: [''], includeUndefined: false};

  componentDidMount() {
    if (this.props.filter.values.length === 0) {
      this.addValue();
    }
    this.props.setValid(this.selectionIsValid());
  }

  setOperator = (operator) => (evt) => {
    evt.preventDefault();

    let {values, includeUndefined} = this.props.filter;
    if (operator === '<' || operator === '>') {
      values = [values[0]];
      includeUndefined = false;
    }

    this.props.changeFilter({operator, values, includeUndefined});
  };

  selectionIsValid = () => {
    const {values, includeUndefined} = this.props.filter;
    const cleanedValues = cleanEmpty(values);
    const allInputsValid = values.every(this.isValid);

    return allInputsValid || (includeUndefined && (allInputsValid || !cleanedValues.length));
  };

  isValid = (value) => value.trim() && !isNaN(value.trim());

  addValue = () => this.props.changeFilter(update(this.props.filter, {values: {$push: ['']}}));

  removeValue = (index) => {
    this.props.changeFilter({
      ...this.props.filter,
      values: this.props.filter.values.filter((_, idx) => idx !== index),
    });
  };

  changeValue = ({target}) => {
    const values = [...this.props.filter.values];
    values[target.getAttribute('data-idx')] = target.value;

    this.props.changeFilter({...this.props.filter, values});
  };

  componentDidUpdate(prevProps) {
    if (prevProps.filter !== this.props.filter) {
      this.props.setValid(this.selectionIsValid());
    }
  }

  render() {
    const {operator, values} = this.props.filter;
    const onlyOneValueAllowed = operator === '<' || operator === '>';

    return (
      <div className="NumberInput">
        <ButtonGroup className="buttonRow">
          <Button onClick={this.setOperator('in')} active={operator === 'in'}>
            {t('common.filter.list.operators.is')}
          </Button>
          <Button onClick={this.setOperator('not in')} active={operator === 'not in'}>
            {t('common.filter.list.operators.not')}
          </Button>
          <Button onClick={this.setOperator('<')} active={operator === '<'}>
            {t('common.filter.list.operators.less')}
          </Button>
          <Button onClick={this.setOperator('>')} active={operator === '>'}>
            {t('common.filter.list.operators.greater')}
          </Button>
        </ButtonGroup>
        <div className="valueFields">
          <ul className="valueList valueListInputs">
            {(operator === 'in' || operator === 'not in') && (
              <LabeledInput
                className="undefinedOption"
                type="checkbox"
                checked={this.props.filter.includeUndefined}
                label={t('common.nullOrUndefined')}
                onChange={({target: {checked}}) =>
                  this.props.changeFilter(
                    update(this.props.filter, {includeUndefined: {$set: checked}})
                  )
                }
              />
            )}
            {(values || []).map((value, idx) => {
              return (
                <li key={idx} className="valueListItem">
                  <Input
                    type="text"
                    value={value}
                    data-idx={idx}
                    onChange={this.changeValue}
                    placeholder={t('common.filter.variableModal.enterValue')}
                  />
                  {values.length > 1 && (
                    <Button
                      onClick={(evt) => {
                        evt.preventDefault();
                        this.removeValue(idx);
                      }}
                      className="removeItemButton"
                    >
                      ×
                    </Button>
                  )}
                </li>
              );
            })}
            {!onlyOneValueAllowed && (
              <li className="valueListButton">
                <Button onClick={this.addValue} className="addValueButton">
                  {t('common.filter.variableModal.addValue')}
                </Button>
              </li>
            )}
          </ul>
        </div>
      </div>
    );
  }

  static parseFilter = ({
    data: {
      data: {operator, values},
    },
  }) => ({
    operator,
    values: values.filter((val) => val !== null),
    includeUndefined: values.includes(null),
  });

  static addFilter = (addFilter, type, variable, {operator, values, includeUndefined}) => {
    const cleanedValues = cleanEmpty(values);
    addFilter({
      type,
      data: {
        name: variable.id || variable.name,
        type: variable.type,
        data: {
          operator,
          values: includeUndefined ? [...cleanedValues, null] : cleanedValues,
        },
      },
    });
  };
}

function cleanEmpty(values) {
  return values.filter((val) => val !== '');
}
