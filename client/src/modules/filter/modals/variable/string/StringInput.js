/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import debounce from 'debounce';
import classnames from 'classnames';

import {ButtonGroup, Button, Checklist} from 'components';
import {t} from 'translation';

import ValueListInput from '../ValueListInput';

import './StringInput.scss';

const valuesToLoad = 10;

export default class StringInput extends React.Component {
  static defaultFilter = {operator: 'in', values: []};

  state = {
    loading: false,
    valueFilter: '',
    availableValues: [],
    valuesLoaded: 0,
    valuesAreComplete: false,
    numberOfUnselectedValuesToDisplay: valuesToLoad,
  };

  reset() {
    this.props.setValid(this.props.filter.values.length > 0);
    this.setState({loading: true});
    this.loadAvailableValues();
  }

  componentDidMount() {
    this.reset();
  }

  componentDidUpdate(prevProps) {
    if (prevProps.variable !== this.props.variable) {
      this.reset();
    }
  }

  loadAvailableValues = debounce(async (more) => {
    const values = await this.props.config.getValues(
      this.props.variable.id || this.props.variable.name,
      this.props.variable.type,
      this.state.valuesLoaded + valuesToLoad + this.props.filter.values.length + 1,
      this.state.valueFilter
    );

    const numberOfUnselectedValuesToDisplay =
      this.state.numberOfUnselectedValuesToDisplay + (more ? valuesToLoad : 0);

    const availableValues = values.slice(
      0,
      numberOfUnselectedValuesToDisplay + this.selectedAvailableValues(values).length
    );

    const valuesAreComplete =
      values.length <
      this.state.valuesLoaded + valuesToLoad + this.availableSelectedValues(values).length + 1;

    this.setState({
      availableValues: [null, ...availableValues],
      valuesLoaded: availableValues.length,
      numberOfUnselectedValuesToDisplay,
      valuesAreComplete,
      loading: false,
    });
  }, 300);

  selectedAvailableValues = (availableValues) => {
    return availableValues.filter((value) => this.props.filter.values.includes(value));
  };

  availableSelectedValues = (availableValues) => {
    return this.props.filter.values.filter((value) => availableValues.includes(value));
  };

  setOperator = (operator) => (evt) => {
    evt.preventDefault();
    const {filter, changeFilter, setValid} = this.props;

    const containToEquality =
      filter.operator.includes('contains') && !operator.includes('contains');
    const equalityToContain =
      !filter.operator.includes('contains') && operator.includes('contains');

    let newValues = filter.values;
    if (containToEquality || equalityToContain) {
      newValues = [];
      setValid(false);
    }

    changeFilter({operator, values: newValues});
  };

  loadMore = (evt) => {
    evt.preventDefault();
    this.setState({loading: true});
    this.loadAvailableValues(true);
  };

  setValueFilter = async (valueFilter) => {
    const queryIncluded = this.state.valueFilter.slice(0, -1) === valueFilter;
    this.setState(
      {
        valueFilter,
        valuesLoaded: queryIncluded ? this.props.filter.values.length : 0,
        numberOfUnselectedValuesToDisplay: valuesToLoad,
        loading: true,
      },
      this.loadAvailableValues
    );
  };

  updateSelected = (newValues) => {
    this.props.changeFilter({
      operator: this.props.filter.operator,
      values: newValues,
    });
    this.props.setValid(newValues.length > 0);
  };

  render() {
    const {changeFilter, setValid, filter} = this.props;
    const {operator, values} = filter;

    const notNullValues = values.filter((val) => val !== null);
    const hasMore = !this.state.valuesAreComplete && !this.state.loading;

    return (
      <div className={classnames('StringInput', {hasMore})}>
        <div className="buttonRow">
          <ButtonGroup>
            <Button onClick={this.setOperator('in')} active={operator === 'in'}>
              {t('common.filter.list.operators.is')}
            </Button>
            <Button onClick={this.setOperator('not in')} active={operator === 'not in'}>
              {t('common.filter.list.operators.not')}
            </Button>
            <Button onClick={this.setOperator('contains')} active={operator === 'contains'}>
              {t('common.filter.list.operators.contains')}
            </Button>
            <Button onClick={this.setOperator('not contains')} active={operator === 'not contains'}>
              {t('common.filter.list.operators.notContains')}
            </Button>
          </ButtonGroup>
        </div>
        {operator.includes('contains') ? (
          <ValueListInput
            filter={{
              operator,
              values: notNullValues.length ? notNullValues : [],
              includeUndefined: values.includes(null),
            }}
            onChange={({operator, values, includeUndefined}) => {
              changeFilter({operator, values: includeUndefined ? [...values, null] : values});
              setValid(includeUndefined || values.length > 0);
            }}
            allowUndefined
            allowMultiple
          />
        ) : (
          <div className="valueSelection">
            <span className="title">{t('common.filter.variableModal.multiSelect.header')}</span>
            <Checklist
              selectedItems={values}
              allItems={this.state.availableValues}
              onSearch={this.setValueFilter}
              onChange={this.updateSelected}
              loading={this.state.loading}
              formatter={(values, selectedValues) =>
                values.map((value) => ({
                  id: value,
                  label: formatValue(value),
                  checked: selectedValues.includes(value),
                }))
              }
              labels={{
                search: t('common.filter.variableModal.multiSelect.search'),
                empty: t('common.filter.variableModal.multiSelect.empty'),
              }}
            />
            {hasMore && (
              <Button
                className="loadMore"
                onClick={this.loadMore}
                disabled={this.state.loading && this.props.disabled}
                link
              >
                {t('common.filter.variableModal.loadMore')}
              </Button>
            )}
          </div>
        )}
      </div>
    );
  }

  static addFilter = (addFilter, type, variable, {operator, values}) => {
    addFilter({
      type,
      data: {
        name: variable.id || variable.name,
        type: variable.type,
        data: {
          operator,
          values: values.filter((val) => val !== ''),
        },
      },
    });
  };
}

function formatValue(val) {
  return val === null ? t('common.nullOrUndefined') : val;
}
