/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import update from 'immutability-helper';
import classnames from 'classnames';
import {MultiValueInput} from '@camunda/camunda-optimize-composite-components';
import {Checkbox, Stack, TextInput} from '@carbon/react';

import {t} from 'translation';

import './ValueListInput.scss';

export default function ValueListInput({
  filter,
  allowUndefined,
  allowMultiple,
  onChange,
  className,
  isValid = () => true,
  errorMessage,
}) {
  const [value, setValue] = useState('');
  const {includeUndefined, values} = filter;

  function updateValues(values) {
    onChange({...filter, values});
  }

  function addValue(value) {
    const trimmedValue = value.trim();

    if (trimmedValue) {
      updateValues([...values, trimmedValue]);
    }
    setValue('');
  }

  function removeValue(_, idx) {
    updateValues(values.filter((_, index) => idx !== index));
  }

  function handlePaste(evt) {
    const paste = (evt.clipboardData || window.clipboardData).getData('text');
    evt.preventDefault();

    const newValues = paste.match(/[^\s]+/g);

    if (values) {
      updateValues([...values, ...newValues]);
    }
  }

  const valueObjects = values.map((value) => ({value, invalid: !isValid(value)}));

  function handleKeyDown(evt) {
    if (['Enter', 'Tab', ',', ';', ' '].includes(evt.key)) {
      if (value) {
        evt.preventDefault();
      }
      addValue(value);
    }
    if (value === '' && evt.key === 'Backspace' && values.length > 0) {
      const lastElementIndex = valueObjects.length - 1;
      removeValue(valueObjects[lastElementIndex].value, lastElementIndex);
    }
  }

  return (
    <Stack gap={6} className={classnames('ValueListInput', className)}>
      {!allowMultiple && (
        <TextInput
          id="singeValueInput"
          labelText={t('common.value')}
          className="singeValueInput"
          value={values[0] || ''}
          onChange={({target}) => updateValues(target.value ? [target.value] : [])}
          placeholder={t('common.filter.variableModal.enterValue')}
        />
      )}
      {allowMultiple && (
        <MultiValueInput
          id="multipleValuesInput"
          value={value}
          placeholder={t('common.filter.variableModal.enterMultipleValues')}
          values={valueObjects}
          onRemove={removeValue}
          onPaste={handlePaste}
          onChange={({target: {value}}) => setValue(value)}
          onKeyDown={handleKeyDown}
          onBlur={() => addValue(value)}
          invalid={!!errorMessage}
          invalidText={errorMessage}
        />
      )}
      {allowUndefined && (
        <Checkbox
          id="undefinedOption"
          className="undefinedOption"
          checked={includeUndefined}
          labelText={t('common.nullOrUndefined')}
          onChange={({target: {checked}}) =>
            onChange(update(filter, {includeUndefined: {$set: checked}}))
          }
        />
      )}
    </Stack>
  );
}
