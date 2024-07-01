/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {InlineNotification} from '@carbon/react';

import {t} from 'translation';
import {incompatibleFilters} from 'services';

import './ReportWarnings.scss';

export default function ReportWarnings({
  report: {
    data: {view, groupBy, filter},
  },
}) {
  return (
    <>
      {incompatibleFilters(filter, view) && (
        <InlineNotification
          kind="warning"
          hideCloseButton
          subtitle={t('common.filter.incompatibleFilters')}
          className="incompatibleFiltersWarning"
        />
      )}

      {['userTask', 'flowNode'].includes(view.entity) &&
        groupBy?.type === 'endDate' &&
        filter?.some(({type}) => type === 'runningFlowNodesOnly') && (
          <InlineNotification
            kind="warning"
            hideCloseButton
            subtitle={t('report.runningEndedFlowNodeWarning')}
            className="runningEndedFlowNodeWarning"
          />
        )}
    </>
  );
}
