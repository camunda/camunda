/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {MessageBox} from 'components';
import {t} from 'translation';

import {incompatibleFilters} from 'services';

export default function ReportWarnings({
  report: {
    result,
    data: {view, groupBy, filter},
  },
}) {
  const showIncompleteResultWarning = () => {
    if (!result || typeof result.isComplete === 'undefined') {
      return false;
    }

    return !result.isComplete;
  };

  return (
    <>
      {showIncompleteResultWarning() && (
        <MessageBox type="warning">
          {t('report.incomplete', {
            count: result.data.length || Object.keys(result.data).length,
          })}
        </MessageBox>
      )}

      {incompatibleFilters(filter, view) && (
        <MessageBox type="warning">{t('common.filter.incompatibleFilters')}</MessageBox>
      )}

      {['userTask', 'flowNode'].includes(view.entity) &&
        groupBy?.type === 'endDate' &&
        filter?.some(({type}) => type === 'runningFlowNodesOnly') && (
          <MessageBox type="warning">{t('report.runningEndedFlowNodeWarning')}</MessageBox>
        )}
    </>
  );
}
