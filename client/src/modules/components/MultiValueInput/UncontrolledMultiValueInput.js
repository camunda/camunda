/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useRef, useEffect, useImperativeHandle} from 'react';
import classnames from 'classnames';

import {Input, Icon, Tag} from 'components';

import './UncontrolledMultiValueInput.scss';

export default React.forwardRef(function UncontrolledMultiValueInput(
  {values, value, onClear, placeholder, onRemove, className, inputClassName, ...props},
  ref
) {
  const input = useRef();
  const sizer = useRef();

  useImperativeHandle(ref, () => input.current);

  useEffect(() => {
    resize();
  }, [value]);

  function resize() {
    if (input.current && sizer.current) {
      sizer.current.textContent = input.current.value;
      sizer.current.style.display = 'inline-block';
      input.current.style.width = sizer.current.getBoundingClientRect().width + 'px';
      sizer.current.style.display = 'none';
    }
  }

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

  const empty = values.length === 0;

  return (
    <div
      className={classnames('UncontrolledMultiValueInput', className)}
      onClick={() => input.current?.focus()}
    >
      {empty && value === '' && <span className="placeholder">{placeholder}</span>}
      <Input
        className={inputClassName}
        // https://stackoverflow.com/a/30976223/4016581
        autoComplete="none"
        value={value}
        ref={input}
        {...props}
      />
      <span className="sizer" ref={sizer} />
      {values.map(({value, label, invalid}, i) => (
        <Tag key={i} invalid={invalid} onRemove={() => onRemove(value, i)}>
          {label || value}
        </Tag>
      ))}
      {!empty && (
        <button className="searchClear" onKeyDown={tiggerClear} onMouseDown={tiggerClear}>
          <Icon type="clear" />
        </button>
      )}
    </div>
  );
});
