/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Labeled, Input} from 'components';
import classnames from 'classnames';

export default function LabeledInput({label, className, children, ...props}) {
  return (
    <div className={classnames('LabeledInput', className)}>
      <Labeled
        id={props.id}
        label={label}
        appendLabel={props.type === 'checkbox' || props.type === 'radio'}
        disabled={props.disabled}
      >
        <Input {...props} />
      </Labeled>
      {children}
    </div>
  );
}
