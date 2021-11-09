/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Labeled, Typeahead} from 'components';
import {t} from 'translation';

import {BooleanInput} from './boolean';
import {NumberInput} from './number';
import {StringInput} from './string';
import {DateInput} from './date';

import './FilterInstance.scss';

export default function FilterInstance({
  filter,
  variables,
  updateFilterData,
  filters,
  config,
  applyTo,
}) {
  const getInputComponentForVariable = (type) => {
    if (!type) {
      return () => null;
    }

    switch (type.toLowerCase()) {
      case 'string':
        return StringInput;
      case 'boolean':
        return BooleanInput;
      case 'date':
        return DateInput;
      default:
        return NumberInput;
    }
  };

  const selectVariable = (value) => {
    const nameAndType = value.split('_');
    const type = nameAndType.pop();
    const name = nameAndType.join('_');
    const variable = variables.find((variable) => variable.name === name && variable.type === type);

    updateFilterData({
      name,
      type,
      data: getInputComponentForVariable(variable.type).defaultFilter,
    });
  };

  const InputComponent = getInputComponentForVariable(filter.type);

  return (
    <section className="FilterInstance">
      <Labeled className="LabeledTypeahead" label={t('common.filter.variableModal.inputLabel')}>
        <Typeahead
          onChange={selectVariable}
          value={variables.length > 0 ? filter.name + '_' + filter.type : undefined}
          placeholder={t('common.filter.variableModal.inputPlaceholder')}
          noValuesMessage={t('common.filter.variableModal.noVariables')}
        >
          {variables.map((variable) => (
            <Typeahead.Option
              key={variable.name + '_' + variable.type}
              value={variable.name + '_' + variable.type}
              disabled={filters.some(
                (filter) => filter.name === variable.name && filter.type === variable.type
              )}
            >
              {variable.name}
            </Typeahead.Option>
          ))}
        </Typeahead>
      </Labeled>
      <InputComponent
        config={config}
        variable={{name: filter.name, type: filter.type}}
        changeFilter={(data) => {
          updateFilterData({...filter, data});
        }}
        filter={filter.data}
        definition={applyTo}
      />
    </section>
  );
}
