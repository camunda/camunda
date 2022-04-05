/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useMemo, useState} from 'react';
import debounce from 'debounce';

import {Icon, LoadingIndicator, Tooltip} from 'components';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import GoalResult from './GoalResult';
import {evaluateGoals} from './service';

import './ResultPreview.scss';

export function ResultPreview({mightFail, processDefinitionKey, goals}) {
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);

  const evaluateGoalsDebounced = useMemo(
    () =>
      debounce((goals, processDefinitionKey) => {
        mightFail(
          evaluateGoals(processDefinitionKey, goals),
          (results) => {
            setResults(results);
            setLoading(false);
          },
          showError
        );
      }, 300),
    [mightFail]
  );

  useEffect(() => {
    setLoading(true);
    evaluateGoalsDebounced(goals, processDefinitionKey);
  }, [evaluateGoalsDebounced, goals, processDefinitionKey]);

  return (
    <div className="ResultPreview">
      <div className="resultHeading">
        <span className="title">{t('processes.timeGoals.resultPreview')} </span>
        {t('processes.timeGoals.instancesThisMonth')}{' '}
        <Tooltip position="bottom" content={t('processes.timeGoals.resultInfo')}>
          <Icon type="info" />
        </Tooltip>
      </div>
      {loading ? <LoadingIndicator /> : <GoalResult durationGoals={{goals, results}} displayTip />}
    </div>
  );
}

export default withErrorHandling(ResultPreview);
