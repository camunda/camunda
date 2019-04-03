/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import './ControlGroup.scss';

export default function ControlGroup(props) {
  return (
    <div
      className={classnames(props.className, 'ControlGroup', {
        ['ControlGroup--' + props.layout]: props.layout
      })}
    >
      {props.children}
    </div>
  );
}
