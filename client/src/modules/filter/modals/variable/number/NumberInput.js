/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {ButtonGroup, Button} from 'components';
import {t} from 'translation';

import ValueListInput from '../ValueListInput';

export default class NumberInput extends React.Component {
  static defaultFilter = {operator: 'in', values: [], includeUndefined: false};

  componentDidMount() {
    this.props.setValid?.(this.selectionIsValid());
  }

  setOperator = (newOperator) => (evt) => {
    evt.preventDefault();

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

  componentDidUpdate(prevProps) {
    if (prevProps.filter !== this.props.filter) {
      this.props.setValid?.(this.selectionIsValid());
    }
  }

  render() {
    const {filter, changeFilter} = this.props;
    const {values, operator} = filter;
    const hasInvalidValue = values.length > 0 && !this.selectionIsValid();

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
          filter={filter}
          isValid={this.isValid}
          onChange={changeFilter}
          allowUndefined={operator === 'in' || operator === 'not in'}
          allowMultiple={operator !== '<' && operator !== '>'}
          errorMessage={hasInvalidValue ? t('common.filter.variableModal.invalidInput') : undefined}
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
