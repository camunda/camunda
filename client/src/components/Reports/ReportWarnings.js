/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {MessageBox} from 'components';
import {t} from 'translation';

import {incompatibleFilters} from 'services';

export default function ReportWarnings({
  report: {
    data: {view, groupBy, filter},
  },
}) {
  return (
    <>
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
