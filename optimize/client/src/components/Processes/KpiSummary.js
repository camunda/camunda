/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {CheckmarkFilled, Misuse} from '@carbon/icons-react';

import {isSuccessful} from './service';

import './KpiSummary.scss';

export default function KpiSummary({kpis}) {
  if (!kpis || kpis.length === 0) {
    return null;
  }

  const succeededKpis = kpis.filter(isSuccessful);
  const failedKpis = kpis.filter((args) => !isSuccessful(args));

  return (
    <div className="KpiSummary">
      {succeededKpis.length > 0 && (
        <div>
          <CheckmarkFilled className="success" />
          <span>{succeededKpis.length}</span>
        </div>
      )}
      {failedKpis.length > 0 && (
        <div>
          <Misuse className="error" />
          <span>{failedKpis.length}</span>
        </div>
      )}
    </div>
  );
}
