/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import {isDurationReport} from 'services';

export default function PieChartConfig({onChange, report}) {
  const {
    data: {configuration}
  } = report;

  return (
    <fieldset>
      <legend>Always show tooltips</legend>
      <RelativeAbsoluteSelection
        hideRelative={isDurationReport(report)}
        absolute={configuration.alwaysShowAbsolute}
        relative={configuration.alwaysShowRelative}
        onChange={(type, value) => {
          if (type === 'absolute') {
            onChange({alwaysShowAbsolute: {$set: value}});
          } else {
            onChange({alwaysShowRelative: {$set: value}});
          }
        }}
      />
    </fieldset>
  );
}
