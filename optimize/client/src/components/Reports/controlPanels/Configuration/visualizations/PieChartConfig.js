/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FormGroup} from '@carbon/react';

import {isDurationReport} from 'services';
import {t} from 'translation';

import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';

export default function PieChartConfig({onChange, report}) {
  const {
    data: {configuration},
  } = report;

  return (
    <FormGroup legendText={t('report.config.tooltips.legend')}>
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
    </FormGroup>
  );
}
