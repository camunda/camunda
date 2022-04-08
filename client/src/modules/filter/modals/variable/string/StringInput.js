/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';
import deepEqual from 'fast-deep-equal';

import {ButtonGroup, Button, Checklist, Input, Icon} from 'components';
import {t} from 'translation';
import debouncePromise from 'debouncePromise';

import ValueListInput from '../ValueListInput';

import './StringInput.scss';

const valuesToLoad = 10;

const debounceRequest = debouncePromise();

export default class StringInput extends React.Component {
  static defaultFilter = {operator: 'in', values: []};

  state = {
    loading: false,
    valueFilter: '',
    availableValues: [],
    valuesLoaded: 0,
    valuesAreComplete: false,
    numberOfUnselectedValuesToDisplay: valuesToLoad,
    showCustomValueInput: false,
    showCustomValueSuccess: false,
    customValue: '',
  };

  reset() {
    this.props.setValid?.(this.props.filter.values.length > 0);
    this.loadAvailableValues();
  }

  componentDidMount() {
    this.reset();
  }

  componentDidUpdate(prevProps) {
    if (deepEqual(prevProps.variable !== this.props.variable)) {
      this.reset();
    }
  }

  loadAvailableValues = async (more) => {
    this.setState({loading: true});

    const stateUpdate = await debounceRequest(async () => {
      const values = await this.props.config.getValues(
        this.props.variable.id || this.props.variable.name,
        this.props.variable.type,
        this.state.valuesLoaded + valuesToLoad + this.props.filter.values.length + 1,
        this.state.valueFilter,
        this.props.definition
      );

      const numberOfUnselectedValuesToDisplay =
        this.state.numberOfUnselectedValuesToDisplay + (more ? valuesToLoad : 0);

      // create a sorted array of all values that should be displayed
      const availableValues = values
        .slice(0, numberOfUnselectedValuesToDisplay + this.selectedAvailableValues(values).length)
        .sort();

      const valuesAreComplete =
        values.length <
        this.state.valuesLoaded + valuesToLoad + this.availableSelectedValues(values).length + 1;

      // Custom values are values that appear in this.props.filter.values, but are never returned as available value from the backend.
      // These are values the customer can add before they appear in the process. We need to manually add them to the list of
      // available values. For that, we need to check the alphabetically biggest value that we display in the Checklist. If we
      // find a value in the filter values array that should be there but is not, we add it to the available values.
      const maxAvailableValue = availableValues[availableValues.length - 1];
      const sortedAddedValues = [...this.props.filter.values].sort();

      sortedAddedValues.forEach((value) => {
        if (
          (value < maxAvailableValue || valuesAreComplete) &&
          !availableValues.includes(value) &&
          value !== null
        ) {
          availableValues.push(value);
        }
      });

      availableValues.sort();

      return {
        availableValues: [null, ...availableValues],
        valuesLoaded: availableValues.length,
        numberOfUnselectedValuesToDisplay,
        valuesAreComplete,
        loading: false,
      };
    }, 300);

    this.setState({...stateUpdate, loading: false});
  };

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
      setValid?.(false);
    }

    changeFilter({operator, values: newValues});

    if (containToEquality) {
      this.loadAvailableValues();
    }
  };

  loadMore = (evt) => {
    evt.preventDefault();
    this.loadAvailableValues(true);
  };

  setValueFilter = async (valueFilter) => {
    const queryIncluded = this.state.valueFilter.slice(0, -1) === valueFilter;
    this.setState(
      {
        valueFilter,
        valuesLoaded: queryIncluded ? this.props.filter.values.length : 0,
        numberOfUnselectedValuesToDisplay: valuesToLoad,
      },
      this.loadAvailableValues
    );
  };

  updateSelected = (newValues) => {
    this.props.changeFilter({
      operator: this.props.filter.operator,
      values: newValues,
    });
    this.props.setValid?.(newValues.length > 0);
  };

  addCustomValue = () => {
    const {
      filter: {values},
    } = this.props;
    const {valuesAreComplete, availableValues, customValue} = this.state;

    this.setState({
      showCustomValueInput: false,
      customValue: '',
      showCustomValueSuccess: true,
    });

    if (!values.includes(customValue)) {
      this.updateSelected([...values, customValue]);

      // if the newly added custom value should also be displayed in the Checklist (and not hidden
      // behind a potential "Load More" button), we also need to add it to the available values
      const maxAvailableValue = availableValues[availableValues.length - 1];
      if (
        !availableValues.includes(customValue) &&
        (customValue < maxAvailableValue || valuesAreComplete)
      ) {
        this.setState({
          availableValues: [
            null, // null option is always at first position; we need to remove it from previous availableValues array when sorting
            ...[...availableValues.filter((value) => value !== null), customValue].sort(),
          ],
        });
      }
    }
  };

  render() {
    const {changeFilter, setValid, filter} = this.props;
    const {
      valuesAreComplete,
      loading,
      availableValues,
      showCustomValueInput,
      showCustomValueSuccess,
      customValue,
    } = this.state;
    const {operator, values} = filter;

    const notNullValues = values.filter((val) => val !== null);
    const hasMore = !valuesAreComplete && !loading;
    const containMode = operator.includes('contains');

    return (
      <div className={classnames('StringInput', {hasMore, containMode})}>
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
              setValid?.(includeUndefined || values.length > 0);
            }}
            allowUndefined
            allowMultiple
          />
        ) : (
          <div className="valueSelection">
            <Checklist
              customHeader={t('common.filter.variableModal.multiSelect.header')}
              selectedItems={values}
              allItems={availableValues}
              onSearch={this.setValueFilter}
              onChange={this.updateSelected}
              loading={loading}
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
            {showCustomValueInput && (
              <div className="customValueInput">
                <Input
                  value={customValue}
                  placeholder={t('common.filter.variableModal.customValue')}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      this.addCustomValue();
                    }
                  }}
                  onChange={(e) => this.setState({customValue: e.target.value})}
                />
                <Button disabled={!customValue} onClick={this.addCustomValue}>
                  {t('common.filter.variableModal.addToList')}
                </Button>
              </div>
            )}
            <div className="customValueButtonRow">
              {showCustomValueSuccess && (
                <div className="notification">{t('common.filter.variableModal.addedToList')}</div>
              )}
              <Button
                small
                disabled={showCustomValueInput}
                className="customValueButton"
                onClick={() => {
                  this.setState({showCustomValueInput: true, showCustomValueSuccess: false});
                }}
              >
                <Icon type="plus" />
                {t('common.value')}
              </Button>
            </div>
          </div>
        )}
      </div>
    );
  }

  static addFilter = (addFilter, type, variable, {operator, values}, applyTo) => {
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
      appliedTo: [applyTo?.identifier],
    });
  };

  static isValid = ({includeUndefined, values}) => {
    return includeUndefined || values.length > 0;
  };
}

function formatValue(val) {
  return val === null ? t('common.nullOrUndefined') : val;
}
