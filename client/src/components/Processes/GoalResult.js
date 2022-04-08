/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classNames from 'classnames';

import {t} from 'translation';
import {formatters} from 'services';
import {NoDataNotice} from 'components';

import './GoalResult.scss';

export default function GoalResult({durationGoals: {goals, results}, displayTip}) {
  if (!results || results.every((goal) => !goal.value)) {
    return (
      <div className="GoalResult">
        <NoDataNotice type="info">
          {t('processes.timeGoals.noInstances')} {displayTip && t('processes.timeGoals.setGoals')}
        </NoDataNotice>
      </div>
    );
  }

  return (
    <div className="GoalResult">
      {goals?.map((goal, idx) => {
        return (
          <div key={idx} className="goal">
            <p>
              <b>{goal.percentile}%</b> {t('processes.timeGoals.instancesTook')}{' '}
              <b className={classNames('duration', {success: results[idx]?.successful})}>
                {' '}
                {formatters.duration(results[idx]?.value, 1)}
              </b>
            </p>
            <span className="subText">
              {t('processes.timeGoals.' + goal.type)}: {t('processes.timeGoals.lessThan')}{' '}
              {formatters.duration({value: goal.value, unit: goal.unit}, 1)}
            </span>
          </div>
        );
      })}
    </div>
  );
}
