/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {KeyboardEvent, useRef, useState} from 'react';

import UncontrolledMultiValueInput, {
  UncontrolledMultiValueInputProps,
} from './UncontrolledMultiValueInput';

interface MultiValueInputProps<T = unknown>
  extends Omit<UncontrolledMultiValueInputProps<T>, 'onChange'> {
  onAdd: (value: string) => void;
  onChange?: (value: string) => void;
  extraSeperators?: string[];
}

export default function MultiValueInput<T = unknown>({
  children,
  onClear,
  values = [],
  onRemove,
  onAdd,
  onChange,
  extraSeperators = [],
  ...props
}: MultiValueInputProps<T>) {
  const [value, setValue] = useState('');
  const input = useRef<HTMLInputElement>(null);

  function handleKeyPress(evt: KeyboardEvent) {
    if (['Enter', 'Tab', ...extraSeperators].includes(evt.key)) {
      if (value) {
        evt.preventDefault();
      }
      addValue();
    }
    if (value === '' && evt.key === 'Backspace' && values.length > 0) {
      const lastElementIndex = values.length - 1;
      onRemove(values[lastElementIndex]!.value, lastElementIndex);
    }
  }

  function addValue() {
    onAdd(value);
    setValue('');
  }

  return (
    <UncontrolledMultiValueInput
      {...props}
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
    />
  );
}
