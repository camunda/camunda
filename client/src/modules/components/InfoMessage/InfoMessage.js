/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import classnames from 'classnames';

import './InfoMessage.scss';

export default function InfoMessage(props) {
  return <div className={classnames('InfoMessage', props.className)}>{props.children}</div>;
}
