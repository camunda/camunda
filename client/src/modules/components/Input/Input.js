/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import {Icon} from 'components';

import './Input.scss';

export default React.forwardRef(function Input({isInvalid, onClear, ...props}, ref) {
  return (
    <>
      <input
        required={!!onClear}
        type="text"
        {...props}
        className={classnames('Input', props.className, {isInvalid})}
        ref={ref}
      >
        {props.children}
      </input>
      {onClear && (
        <button className="searchClear" onClick={onClear}>
          <Icon type="clear" />
        </button>
      )}
    </>
  );
});
