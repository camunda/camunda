/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {forwardRef, ComponentPropsWithoutRef, KeyboardEvent, MouseEvent} from 'react';
import classnames from 'classnames';
import {Icon} from 'components';

import './Input.scss';

// We are overriding here the default placeholder type to let the use of translation function without type casting
export interface InputProps extends Omit<ComponentPropsWithoutRef<'input'>, 'placeholder'> {
  disabled?: boolean;
  className?: string;
  isInvalid?: boolean;
  placeholder?: string | JSX.Element[];
  onClear?: (evt: KeyboardEvent<HTMLButtonElement> | MouseEvent<HTMLButtonElement>) => void;
}

export default forwardRef<HTMLInputElement, InputProps>(function Input(
  {isInvalid, onClear, placeholder, ...props},
  ref
): JSX.Element {
  if (placeholder && typeof placeholder !== 'string') {
    throw new Error('Input: Placeholder should be of type string');
  }

  let inputEl: HTMLInputElement;
  const setRef = (el: HTMLInputElement) => {
    inputEl = el;
    if (!ref) {
      return;
    }
    if (typeof ref === 'function') {
      return ref(el);
    }
    return (ref.current = el);
  };

  const triggerClear = (evt: KeyboardEvent<HTMLButtonElement> | MouseEvent<HTMLButtonElement>) => {
    if ('type' in evt && 'keyCode' in evt && evt.type === 'keydown' && evt.keyCode !== 13) {
      return;
    }
    onClear?.(evt);
    if (inputEl) {
      inputEl.focus();
    }
    if ('preventDefault' in evt) {
      evt.preventDefault();
    }
  };

  return (
    <>
      <input
        required={!!onClear}
        type="text"
        onDoubleClick={() => {
          if (props.type === 'text') {
            inputEl.select();
          }
        }}
        {...props}
        placeholder={placeholder}
        className={classnames('Input', props.className, {isInvalid})}
        ref={setRef}
      >
        {props.children}
      </input>
      {onClear && (
        <button className="searchClear" onKeyDown={triggerClear} onMouseDown={triggerClear}>
          <Icon type="clear" />
        </button>
      )}
    </>
  );
});
