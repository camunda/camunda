/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classNames from 'classnames';

import {Icon} from 'components';
import {t} from 'translation';

import './GoalSummary.scss';

export default function GoalSummary({goals}) {
  if (goals.length === 0) {
    return null;
  }

  if (goals.every((goal) => !goal.value)) {
    return (
      <div className="GoalSummary">
        <Icon type="info" />
        <span className="height-center">{t('processes.noData')}</span>
      </div>
    );
  }

  const allSucceeded = goals.every((goal) => goal.successful);
  const allFailed = goals.every((goal) => !goal.successful);

  if (allSucceeded || allFailed) {
    return (
      <div className="GoalSummary">
        <Icon
          className={classNames({success: allSucceeded, error: allFailed})}
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
      <Icon type="clear" className="error" />
      <span className="height-center">1</span>
    </div>
  );
}
