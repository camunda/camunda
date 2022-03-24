/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classNames from 'classnames';

import {Icon} from 'components';

import './GoalSummary.scss';

export default function GoalSummary({goals}) {
  if (goals.length === 0 || goals.every((goal) => !goal.value)) {
    return null;
  }
  const allSucceeded = goals.every((goal) => goal.successful);
  const allFailed = goals.every((goal) => !goal.successful);

  if (allSucceeded || allFailed) {
    return (
      <div className="GoalSummary">
        <Icon
          className={classNames({success: allSucceeded})}
          type={allSucceeded ? 'check-circle' : 'clear'}
        />
        <span className="center">{goals.length}</span>
      </div>
    );
  }

  return (
    <div className="GoalSummary">
      <Icon className="success" type="check-circle" />
      <span className="height-center">1</span>
      <Icon type="clear" />
      <span className="height-center">1</span>
    </div>
  );
}
