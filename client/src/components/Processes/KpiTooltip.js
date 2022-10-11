/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {DocsLink, Icon, Tooltip} from 'components';
import {t} from 'translation';

export default function KpiTooltip() {
  return (
    <Tooltip
      align="right"
      content={
        <>
          {t('processes.configureKpis')}{' '}
          <DocsLink location="components/optimize/userguide/processes/#set-time-and-quality-kpis">
            {t('common.here')}
          </DocsLink>
          .
        </>
      }
    >
      <Icon type="info" />
    </Tooltip>
  );
}
