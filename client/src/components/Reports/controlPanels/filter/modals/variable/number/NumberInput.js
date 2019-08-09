/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ButtonGroup, Button, Input} from 'components';

import './NumberInput.scss';
import {t} from 'translation';

export default class NumberInput extends React.Component {
  static defaultFilter = {operator: 'in', values: ['']};

  componentDidMount() {
    this.props.setValid(this.selectionIsValid());
  }

  setOperator = operator => evt => {
    evt.preventDefault();

    let {values} = this.props.filter;
    if (operator === '<' || operator === '>') {
      values = [values[0]];
    }

    this.props.changeFilter({operator, values});
  };

  selectionIsValid = () => {
    const {values} = this.props.filter;

    return (values || []).every(this.isValid);
  };

  isValid = value => value.trim() && !isNaN(value.trim());

  addValue = evt => {
    evt.preventDefault();

    this.props.changeFilter({...this.props.filter, values: [...this.props.filter.values, '']});
  };

  removeValue = index => {
    this.props.changeFilter({
      ...this.props.filter,
      values: this.props.filter.values.filter((_, idx) => idx !== index)
    });
  };

  changeValue = ({target}) => {
    const values = [...this.props.filter.values];
    values[target.getAttribute('data-idx')] = target.value;

    this.props.changeFilter({...this.props.filter, values});
  };

  componentDidUpdate(prevProps) {
    if (prevProps.filter.values !== this.props.filter.values) {
      this.props.setValid(this.selectionIsValid());
    }
  }

  render() {
    const {operator, values} = this.props.filter;
    const onlyOneValueAllowed = operator === '<' || operator === '>';

    return (
      <React.Fragment>
        <div className="VariableFilter__buttonRow">
          <ButtonGroup>
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
        </div>
        <div className="VariableFilter__valueFields">
          <ul className="NumberInput__valueList NumberInput__valueList--inputs">
            {(values || []).map((value, idx) => {
              return (
                <li key={idx} className="NumberInput__valueListItem">
                  <Input
                    type="text"
                    value={value}
                    data-idx={idx}
                    onChange={this.changeValue}
                    placeholder={t('common.filter.variableModal.enterValue')}
                  />
                  {values.length > 1 && (
                    <Button
                      onClick={evt => {
                        evt.preventDefault();
                        this.removeValue(idx);
                      }}
                      className="NumberInput__removeItemButton"
                    >
                      ×
                    </Button>
                  )}
                </li>
              );
            })}
            {!onlyOneValueAllowed && (
              <li className="NumberInput__valueListButton">
                <Button onClick={this.addValue} className="NumberInput__addValueButton">
                  {t('common.filter.variableModal.addValue')}
                </Button>
              </li>
            )}
          </ul>
        </div>
      </React.Fragment>
    );
  }
}
