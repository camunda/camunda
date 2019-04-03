/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Icon} from 'components';
import './DropdownOption.scss';
import classnames from 'classnames';

export default React.forwardRef(function DropdownOption({active, ...props}, ref) {
  return (
    <div
      {...props}
      onClick={evt => !props.disabled && props.onClick && props.onClick(evt)}
      className={classnames('DropdownOption', props.className, {'is-active': active})}
      tabIndex={props.disabled ? '-1' : '0'}
      ref={ref}
    >
      {props.checked && <Icon className="checkMark" type="check-small" size="10px" />}
      {props.children}
    </div>
  );
});
