/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import update from 'immutability-helper';

import {ButtonGroup, Button} from 'components';
import {t} from 'translation';

import ValueListInput from '../ValueListInput';

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

  componentDidUpdate(prevProps) {
    if (prevProps.filter !== this.props.filter) {
      this.props.setValid(this.selectionIsValid());
    }
  }

  render() {
    const {filter, changeFilter} = this.props;
    const {operator} = filter;

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
        <ValueListInput
          className="valueFields"
          filter={filter}
          onChange={changeFilter}
          allowUndefined={operator === 'in' || operator === 'not in'}
          allowMultiple={operator !== '<' && operator !== '>'}
        />
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
