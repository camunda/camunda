/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import {Link} from 'react-router-dom';

import './Button.scss';

export default React.forwardRef(function Button(props, ref) {
  const filteredProps = {...props};
  delete filteredProps.active;
  delete filteredProps.color;
  delete filteredProps.type;
  if (props.tag === 'a') {
    return (
      <Link
        {...filteredProps}
        className={classnames(props.className, 'Button', {
          ['Button--' + props.type]: props.type,
          ['Button--' + props.color]: props.color,
          'is-active': props.active
        })}
      >
        {props.children}
      </Link>
    );
  } else {
    return (
      <button
        {...filteredProps}
        className={classnames(props.className, 'Button', {
          ['Button--' + props.type]: props.type,
          ['Button--' + props.color]: props.color,
          'is-active': props.active
        })}
        ref={ref}
      >
        {props.children}
      </button>
    );
  }
});
