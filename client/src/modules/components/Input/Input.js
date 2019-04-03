/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import './Input.scss';

export default React.forwardRef(function Input(props, ref) {
  const allowedProps = {...props};
  delete allowedProps.isInvalid;

  return (
    <input
      type="text"
      {...allowedProps}
      className={classnames('Input', props.className, {
        isInvalid: props.isInvalid
      })}
      ref={ref}
    >
      {props.children}
    </input>
  );
});
