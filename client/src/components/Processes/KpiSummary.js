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

import {isSuccessful} from './service';

import './KpiSummary.scss';

export default function KpiSummary({kpis}) {
  if (!kpis || kpis.length === 0) {
    return null;
  }

  const kpisWithData = kpis.filter((report) => report.value);

  if (kpisWithData.length === 0) {
    return (
      <div className="KpiSummary">
        <Icon type="info" />
        <span className="height-center">{t('processes.noData')}</span>
      </div>
    );
  }

  const succeededKpis = kpisWithData.filter(isSuccessful);
  const failedKpis = kpisWithData.filter((args) => !isSuccessful(args));

  const allSucceeded = succeededKpis.length === kpisWithData.length;
  const allFailed = failedKpis.length === kpisWithData.length;

  if (allSucceeded || allFailed) {
    return (
      <div className="KpiSummary">
        <Icon
          className={classNames({success: allSucceeded, error: allFailed})}
          type={allSucceeded ? 'check-circle' : 'clear'}
        />
        <span className="center">{kpisWithData.length}</span>
      </div>
    );
  }

  return (
    <div className="KpiSummary">
      <Icon className="success" type="check-circle" />
      <span className="height-center">{succeededKpis.length}</span>
      <Icon type="clear" className="error" />
      <span className="height-center">{failedKpis.length}</span>
    </div>
  );
}
