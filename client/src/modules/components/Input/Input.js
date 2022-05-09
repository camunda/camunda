/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';
import {Icon} from 'components';

import './Input.scss';

export default React.forwardRef(function Input({isInvalid, onClear, ...props}, ref) {
  let inputEl;
  const setRef = (el) => {
    inputEl = el;
    if (!ref) {
      return;
    }
    if (typeof ref === 'function') {
      return ref(el);
    }
    return (ref.current = el);
  };

  const triggerClear = (evt) => {
    if (evt.type === 'keydown' && evt.keyCode !== 13) {
      return;
    }
    onClear(evt);
    if (inputEl) {
      inputEl.focus();
    }
    evt.preventDefault();
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
