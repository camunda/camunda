/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import classnames from 'classnames';

import './ErrorMessage.scss';

export default function ErrorMessage(props) {
  return <div className={classnames('ErrorMessage', props.className)}>{props.children}</div>;
}
