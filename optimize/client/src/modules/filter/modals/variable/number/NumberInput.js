/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Component} from 'react';
import {RadioButton, RadioButtonGroup, Stack} from '@carbon/react';

import {t} from 'translation';

import ValueListInput from '../ValueListInput';

export default class NumberInput extends Component {
  static defaultFilter = {operator: 'in', values: [], includeUndefined: false};

  setOperator = (newOperator) => {
    let {operator, values, includeUndefined} = this.props.filter;

    const equalityToComparison = !['>', '<'].includes(operator) && ['>', '<'].includes(newOperator);
    const comparisonToEquality = ['>', '<'].includes(operator) && !['>', '<'].includes(newOperator);

    if (equalityToComparison || comparisonToEquality) {
      values = equalityToComparison && values[0] ? [values[0]] : [];
      includeUndefined = false;
    }

    this.props.changeFilter({operator: newOperator, values, includeUndefined});
  };

  selectionIsValid = () => {
    const {values, includeUndefined} = this.props.filter;

    if (values.length === 0) {
      return includeUndefined;
    }

    return values.every(this.isValid);
  };

  isValid = (value) => value.trim() && !isNaN(value.trim());

  render() {
    const {filter, changeFilter} = this.props;
    const {values, operator} = filter;
    const hasInvalidValue = values.length > 0 && !this.selectionIsValid();

    return (
      <Stack gap={6} className="NumberInput">
        <RadioButtonGroup
          className="buttonRow"
          name="number-filter-type"
          onChange={this.setOperator}
        >
          <RadioButton labelText={t('common.filter.list.operators.is')} value="in" />
          <RadioButton labelText={t('common.filter.list.operators.not')} value="not in" />
          <RadioButton labelText={t('common.filter.list.operators.less')} value="<" />
          <RadioButton labelText={t('common.filter.list.operators.greater')} value=">" />
        </RadioButtonGroup>
        <ValueListInput
          filter={filter}
          isValid={this.isValid}
          onChange={changeFilter}
          allowUndefined={operator === 'in' || operator === 'not in'}
          allowMultiple={operator !== '<' && operator !== '>'}
          errorMessage={hasInvalidValue ? t('common.filter.variableModal.invalidInput') : undefined}
        />
      </Stack>
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

  static addFilter = (addFilter, type, variable, {operator, values, includeUndefined}, applyTo) => {
    addFilter({
      type,
      data: {
        name: variable.id || variable.name,
        type: variable.type,
        data: {
          operator,
          values: includeUndefined ? [...values, null] : values,
        },
      },
      appliedTo: [applyTo?.identifier],
    });
  };

  static isValid = ({values, includeUndefined}) => {
    if (values.length === 0) {
      return includeUndefined;
    }

    return values.every((value) => value.trim() && !isNaN(value.trim()));
  };
}
