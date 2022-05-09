/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';

import {Input, Tooltip} from 'components';
import './Switch.scss';

export default function Switch({label, ...props}) {
  return (
    <Tooltip content={props.title}>
      <label
        className={classnames(
          'Switch',
          {withLabel: label, disabled: props.disabled},
          props.className
        )}
      >
        <Input type="checkbox" {...props} className="Switch__Input" />
        <span className={classnames('Switch__Slider--round', {disabled: props.disabled})} />
        {label && <span className="label">{label}</span>}
      </label>
    </Tooltip>
  );
}
