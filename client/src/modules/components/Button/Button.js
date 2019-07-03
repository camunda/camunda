/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import {Link} from 'react-router-dom';

import './Button.scss';

export default React.forwardRef(function Button({active, color, type, ...props}, ref) {
  const className = classnames(props.className, 'Button', {
    [type]: type,
    [color]: color,
    isActive: active
  });

  if (props.tag === 'a') {
    return (
      <Link {...props} className={className}>
        {props.children}
      </Link>
    );
  } else {
    return (
      <button {...props} className={className} ref={ref}>
        {props.children}
      </button>
    );
  }
});
