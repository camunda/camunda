/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useEffect, useState} from 'react';

import {Input, Icon, Tag} from 'components';

import './MultiValueInput.scss';

export default function MultiValueInput({
  children,
  onClear,
  placeholder,
  values,
  onRemove,
  onAdd,
  ...props
}) {
  const [value, setValue] = useState('');
  const input = useRef();
  const sizer = useRef();

  const empty = values.length === 0;

  function resize() {
    if (input.current && sizer.current) {
      sizer.current.textContent = input.current.value;
      sizer.current.style.display = 'inline-block';
      input.current.style.width = sizer.current.getBoundingClientRect().width + 'px';
      sizer.current.style.display = 'none';
    }
  }

  useEffect(() => {
    resize();
  }, [value]);

  function tiggerClear(evt) {
    if (evt.type === 'keydown' && evt.keyCode !== 13) {
      return;
    }
    if (input.current) {
      input.current.focus();
    }

    evt.preventDefault();
    onClear(evt);
  }

  function handleKeyPress(evt) {
    if (['Enter', ' ', 'Tab', ','].includes(evt.key)) {
      if (value) {
        evt.preventDefault();
      }
      addValue();
    }
    if (value === '' && evt.key === 'Backspace' && !empty) {
      const lastElementIndex = values.length - 1;
      onRemove(values[lastElementIndex].value, lastElementIndex);
    }
  }

  function addValue() {
    onAdd(value);
    setValue('');
  }

  return (
    <div className="MultiValueInput" onClick={() => input.current?.focus()}>
      {empty && value === '' && <span className="placeholder">{placeholder}</span>}
      <Input
        value={value}
        onChange={(evt) => setValue(evt.target.value)}
        onKeyDown={handleKeyPress}
        ref={input}
        onBlur={addValue}
        // https://stackoverflow.com/a/30976223/4016581
        autoComplete="none"
        {...props}
      />
      <span className="sizer" ref={sizer} />
      {values.map(({value, invalid}, i) => (
        <Tag key={i} invalid={invalid} title={value} onRemove={() => onRemove(value, i)}>
          {value}
        </Tag>
      ))}
      {!empty && (
        <button className="searchClear" onKeyDown={tiggerClear} onMouseDown={tiggerClear}>
          <Icon type="clear" />
        </button>
      )}
    </div>
  );
}
