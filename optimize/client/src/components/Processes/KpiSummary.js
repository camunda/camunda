/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CheckmarkFilled, Misuse} from '@carbon/icons-react';

import {Popover} from 'components';

import {isSuccessful} from './service';
import KpiResult from './KpiResult';

import './KpiSummary.scss';

export default function KpiSummary({kpis}) {
  if (!kpis || kpis.length === 0) {
    return null;
  }

  const succeededKpis = kpis.filter(isSuccessful);
  const failedKpis = kpis.filter((args) => !isSuccessful(args));

  return (
    <Popover
      className="KpiSummary"
      align="bottom"
      trigger={
        <Popover.Button kind="tertiary" size="sm">
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
        </Popover.Button>
      }
    >
      <KpiResult kpis={kpis} />
    </Popover>
  );
}
