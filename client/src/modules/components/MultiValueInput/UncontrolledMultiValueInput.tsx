/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useRef, useEffect, useImperativeHandle, UIEvent} from 'react';
import classnames from 'classnames';
import {Input, Icon, Tag, InputProps} from 'components';

import './UncontrolledMultiValueInput.scss';

interface UncontrolledMultiValueInputProps extends InputProps {
  values: {value: string; label?: string; invalid?: boolean}[];
  value: string;
  onRemove: (value: string, index: number) => void;
  className?: string;
  inputClassName?: string;
}

export default React.forwardRef<HTMLInputElement, UncontrolledMultiValueInputProps>(
  ({values, value, onClear, placeholder, onRemove, className, inputClassName, ...props}, ref) => {
    const input = useRef<HTMLInputElement>(null);
    const sizer = useRef<HTMLSpanElement>(null);

    useImperativeHandle(ref, () => input.current as HTMLInputElement);

    useEffect(() => {
      resize();
    }, [value]);

    function resize() {
      if (input.current && sizer.current) {
        sizer.current.textContent = input.current.value;
        sizer.current.style.display = 'inline-block';
        input.current.style.width = `${sizer.current.getBoundingClientRect().width}px`;
        sizer.current.style.display = 'none';
      }
    }

    function tiggerClear(evt: UIEvent<HTMLElement>) {
      if (input.current) {
        input.current.focus();
      }

      evt.preventDefault();
      onClear?.(evt);
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
          <button
            className="searchClear"
            onKeyDown={(evt) => {
              if (evt.key === 'Enter') {
                tiggerClear(evt);
              }
            }}
            onMouseDown={tiggerClear}
          >
            <Icon type="clear" />
          </button>
        )}
      </div>
    );
  }
);
