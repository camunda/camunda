/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FormGroup, Stack} from '@carbon/react';

import ColumnSelection from './subComponents/ColumnSelection';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import GradientBarsSwitch from './subComponents/GradientBarsSwitch';

export default function TableConfig({report, onChange, autoPreviewDisabled}) {
  let typeSpecificComponent = null;

  const property = report.data.view.properties[0];

  const groupBy = report.data.groupBy.type;

  switch (property) {
    case 'rawData':
      typeSpecificComponent = (
        <ColumnSelection report={report} onChange={onChange} disabled={autoPreviewDisabled} />
      );
      break;
    case 'frequency':
      typeSpecificComponent = (
        <FormGroup legendText="">
          <Stack gap={4}>
            <RelativeAbsoluteSelection
              absolute={!report.data.configuration.hideAbsoluteValue}
              relative={!report.data.configuration.hideRelativeValue}
              onChange={(type, value) => {
                if (type === 'absolute') {
                  onChange({hideAbsoluteValue: {$set: !value}});
                } else {
                  onChange({hideRelativeValue: {$set: !value}});
                }
              }}
            />
            {groupBy === 'matchedRule' && (
              <GradientBarsSwitch configuration={report.data.configuration} onChange={onChange} />
            )}
          </Stack>
        </FormGroup>
      );
      break;
    default:
      typeSpecificComponent = null;
  }

  return typeSpecificComponent;
}
