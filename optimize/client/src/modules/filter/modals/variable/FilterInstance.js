/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState, useEffect} from 'react';
import {
  Button,
  ComboBox,
  InlineNotification,
  TagSkeleton,
  Stack,
  Tag,
  TextInputSkeleton,
} from '@carbon/react';
import {ChevronDown, TrashCan} from '@carbon/icons-react';
import classnames from 'classnames';

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
  const [selectedVariable, setSelectedVariable] = useState(null);

  const getInputComponentForVariable = (variable) => {
    if (!variable || !('type' in variable)) {
      return () => null;
    }

    switch (variable.type.toLowerCase()) {
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

  const InputComponent = getInputComponentForVariable(filter);

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

  useEffect(() => {
    if (filter && variables) {
      setSelectedVariable(() => findVariableForFilter(variables, filter));
    }
  }, [filter, variables]);

  const selectVariable = ({selectedItem}) => {
    setSelectedVariable(selectedItem);
    updateFilterData({
      ...selectedItem,
      data: getInputComponentForVariable(selectedItem).defaultFilter,
    });
  };

  const getVariableName = (variable) => (variable ? variable.label || variable.name : null);

  function handleKeyDown(evt) {
    if ((evt.key === ' ' || evt.key === 'Enter') && evt.target === evt.currentTarget && isValid) {
      toggleExpanded();
    }
  }

  return (
    <section className={classnames('FilterInstance', {collapsed})}>
      <Stack gap={6}>
        {!isNew && filters.length > 1 && (
          <div
            tabIndex={0}
            className={classnames('sectionTitle', {clickable: isValid})}
            onClick={isValid ? toggleExpanded : undefined}
            onKeyDown={handleKeyDown}
          >
            {variables ? (
              <Tag type="blue">{getVariableLabel(variables, filter)}</Tag>
            ) : (
              <TagSkeleton />
            )}
            <span>{t('common.filter.list.operators.is')}…</span>
            {!collapsed && (
              <Button
                size="sm"
                kind="ghost"
                hasIconOnly
                iconDescription={t('common.delete')}
                renderIcon={TrashCan}
                className="removeButton"
                onClick={(evt) => {
                  evt.stopPropagation();
                  onRemove();
                }}
              />
            )}
            {isValid && <ChevronDown className={classnames('sectionToggle', {expanded})} />}
          </div>
        )}
        {variables && !variables.length && (
          <InlineNotification
            kind="warning"
            hideCloseButton
            subtitle={t('common.filter.variableModal.noVariables')}
          />
        )}
        {variables ? (
          <ComboBox
            id="multiVariableSelection"
            titleText={t('common.filter.variableModal.inputLabel')}
            placeholder={t('common.filter.variableModal.inputPlaceholder')}
            disabled={!variables.length}
            items={variables}
            itemToString={getVariableName}
            selectedItem={selectedVariable}
            onChange={selectVariable}
            shouldFilterItem={({item}) =>
              !filters.some((filter) => filter.name === item.name && filter.type === item.type)
            }
          />
        ) : (
          <TextInputSkeleton />
        )}
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
      </Stack>
    </section>
  );
}

function getVariableLabel(variables, {name, type}) {
  return findVariableForFilter(variables, {name, type})?.label || name;
}

function findVariableForFilter(variables, {name, type}) {
  const variableForGivenFilter = variables.find(
    (variable) => variable.name === name && variable.type === type
  );
  return variableForGivenFilter || null;
}
