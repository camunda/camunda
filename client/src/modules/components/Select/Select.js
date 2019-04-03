/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import './Select.scss';

export default function Select(props) {
  const allowedProps = {...props};
  delete allowedProps.isInvalid;

  return (
    <select
      {...allowedProps}
      className={classnames('Select', props.className, {
        'is-invalid': props.isInvalid
      })}
    >
      {props.children}
    </select>
  );
}

Select.Option = function Option(props) {
  return <option {...props}>{props.children}</option>;
};
