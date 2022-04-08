/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useRef, useState} from 'react';

import UncontrolledMultiValueInput from './UncontrolledMultiValueInput';

export default function MultiValueInput({
  children,
  onClear,
  values = [],
  onRemove,
  onAdd,
  onChange,
  extraSeperators = [],
  ...props
}) {
  const [value, setValue] = useState('');
  const input = useRef();

  function handleKeyPress(evt) {
    if (['Enter', 'Tab', ...extraSeperators].includes(evt.key)) {
      if (value) {
        evt.preventDefault();
      }
      addValue();
    }
    if (value === '' && evt.key === 'Backspace' && values.length > 0) {
      const lastElementIndex = values.length - 1;
      onRemove(values[lastElementIndex].value, lastElementIndex);
    }
  }

  function addValue() {
    onAdd(value);
    setValue('');
  }

  return (
    <UncontrolledMultiValueInput
      className="MultiValueInput"
      ref={input}
      value={value}
      values={values}
      onChange={({target: {value}}) => {
        setValue(value);
        if (onChange) {
          onChange(value);
        }
      }}
      onKeyDown={handleKeyPress}
      onBlur={addValue}
      onClear={onClear}
      onRemove={onRemove}
      {...props}
    />
  );
}
