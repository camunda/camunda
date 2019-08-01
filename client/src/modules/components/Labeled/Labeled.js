/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import classnames from 'classnames';
import './Labeled.scss';

export default function Labeled({label, className, appendLabel, children, ...props}) {
  return (
    <div className={classnames('Labeled', className)}>
      <label {...props}>
        {!appendLabel && <span className="label before">{label}</span>}
        {children}
        {appendLabel && <span className="label after">{label}</span>}
      </label>
    </div>
  );
}
