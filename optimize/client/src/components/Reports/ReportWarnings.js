/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
