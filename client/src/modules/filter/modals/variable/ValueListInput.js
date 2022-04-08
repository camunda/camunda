/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import update from 'immutability-helper';
import classnames from 'classnames';

import {MultiValueInput, Input, LabeledInput, Message, Labeled} from 'components';
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
  const {includeUndefined, values} = filter;

  function updateValues(values) {
    onChange({...filter, values});
  }

  function paste(evt) {
    const paste = (evt.clipboardData || window.clipboardData).getData('text');
    evt.preventDefault();
    const newValues = paste.match(/[^\s]+/g);
    if (values) {
      updateValues([...values, ...newValues]);
    }
  }

  return (
    <div className={classnames('ValueListInput', className)}>
      <Labeled label={t('common.value')}>
        {!allowMultiple && (
          <Input
            type="text"
            className="singeValueInput"
            value={values[0] || ''}
            onChange={({target}) => updateValues(target.value ? [target.value] : [])}
            placeholder={t('common.filter.variableModal.enterValue')}
          />
        )}
        {allowMultiple && (
          <MultiValueInput
            placeholder={t('common.filter.variableModal.enterMultipleValues')}
            values={values.map((value) => ({value, invalid: !isValid(value)}))}
            onAdd={(value) => value.trim() && updateValues([...values, value])}
            onRemove={(_, idx) => updateValues(values.filter((_, index) => idx !== index))}
            onClear={() => updateValues([])}
            onPaste={paste}
          />
        )}
      </Labeled>
      {errorMessage && <Message error>{errorMessage}</Message>}
      {allowUndefined && (
        <LabeledInput
          className="undefinedOption"
          type="checkbox"
          checked={includeUndefined}
          label={t('common.nullOrUndefined')}
          onChange={({target: {checked}}) =>
            onChange(update(filter, {includeUndefined: {$set: checked}}))
          }
        />
      )}
    </div>
  );
}
