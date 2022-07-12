/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classNames from 'classnames';

import {Icon} from 'components';

import {isSuccessful} from './service';

import './KpiSummary.scss';

export default function KpiSummary({kpis}) {
  if (!kpis || kpis.length === 0) {
    return null;
  }

  const succeededKpis = kpis.filter(isSuccessful);
  const failedKpis = kpis.filter((args) => !isSuccessful(args));

  const allSucceeded = succeededKpis.length === kpis.length;
  const allFailed = failedKpis.length === kpis.length;

  if (allSucceeded || allFailed) {
    return (
      <div className="KpiSummary">
        <Icon
          className={classNames({success: allSucceeded, error: allFailed})}
          type={allSucceeded ? 'check-circle' : 'clear'}
        />
        <span className="center">{kpis.length}</span>
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
