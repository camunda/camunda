/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';
import classnames from 'classnames';

import {Button, Icon, Labeled, Tooltip, Typeahead} from 'components';
import {t} from 'translation';

import {BooleanInput} from './boolean';
import {NumberInput} from './number';
import {StringInput} from './string';
import {DateInput} from './date';

import './FilterInstance.scss';

export default function FilterInstance({
  expanded,
  toggleExpanded,
  onRemove,
  filter,
  variables,
  updateFilterData,
  filters,
  config,
  applyTo,
}) {
  const [isNew, setIsNew] = useState(true);

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

  const InputComponent = getInputComponentForVariable(filter.type);

  const isValid = InputComponent.isValid?.(filter.data);
  const isMoreThanOneFilter = filters.length > 1;
  const filterIdx = filters.indexOf(filter);

  const collapsed = !expanded && isMoreThanOneFilter && isValid;

  useEffect(() => {
    const isLastFilter = !filters[filterIdx + 1];
    if (!isLastFilter || collapsed) {
      setIsNew(false);
    }
  }, [collapsed, filters, expanded, filterIdx]);

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

  return (
    <section className={classnames('FilterInstance', {collapsed})}>
      {!isNew && filters.length > 1 && (
        <div
          tabIndex="0"
          className={classnames('sectionTitle', {clickable: isValid})}
          onClick={isValid ? toggleExpanded : undefined}
          onKeyDown={(evt) => {
            if (
              (evt.key === ' ' || evt.key === 'Enter') &&
              evt.target === evt.currentTarget &&
              isValid
            ) {
              toggleExpanded();
            }
          }}
        >
          <span className="highlighted">{getVariableLabel(variables, filter)}</span>{' '}
          {t('common.filter.list.operators.is')}…
          {!collapsed && (
            <Tooltip content={t('common.delete')}>
              <Button
                icon
                className="removeButton"
                onClick={(evt) => {
                  evt.stopPropagation();
                  onRemove();
                }}
              >
                <Icon type="delete" />
              </Button>
            </Tooltip>
          )}
          {isValid && (
            <span className={classnames('sectionToggle', {expanded})}>
              <Icon type="down" />
            </span>
          )}
        </div>
      )}
      <Labeled className="LabeledTypeahead" label={t('common.filter.variableModal.inputLabel')}>
        <Typeahead
          onChange={selectVariable}
          value={variables.length > 0 ? filter.name + '_' + filter.type : undefined}
          placeholder={t('common.filter.variableModal.inputPlaceholder')}
          noValuesMessage={t('common.filter.variableModal.noVariables')}
        >
          {variables.map(({name, label, type}) => (
            <Typeahead.Option
              key={name + '_' + type}
              value={name + '_' + type}
              disabled={filters.some((filter) => filter.name === name && filter.type === type)}
            >
              {label || name}
            </Typeahead.Option>
          ))}
        </Typeahead>
      </Labeled>
      {applyTo && (
        <InputComponent
          config={config}
          variable={{name: filter.name, type: filter.type}}
          changeFilter={(data) => {
            updateFilterData({...filter, data});
          }}
          filter={filter.data}
          definition={applyTo}
        />
      )}
    </section>
  );
}

function getVariableLabel(variables, {name, type}) {
  return (
    variables.find((variable) => variable.name === name && variable.type === type)?.label || name
  );
}
