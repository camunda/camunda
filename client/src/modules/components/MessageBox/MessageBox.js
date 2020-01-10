/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import './MessageBox.scss';

export default function MessageBox({type, children, ...props}) {
  return (
    <div
      {...props}
      className={classnames('MessageBox', {
        ['MessageBox--' + type]: type
      })}
    >
      {children}
    </div>
  );
}
