/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Checkbox, FormGroup, Stack, Toggle} from '@carbon/react';

import {TargetSelection} from 'components';
import {t} from 'translation';
import {track} from 'tracking';

export default function NumberConfig({report, onChange}) {
  const {configuration, view, definitions} = report.data;
  const targetValue = configuration.targetValue;

  const isMultiMeasure = report.result?.measures.length > 1;
  const isSingleProcessReport = definitions.length === 1;

  if (isMultiMeasure) {
    return null;
  }

  return (
    <FormGroup
      className="NumberConfig"
      legendText={
        <Toggle
          id="setTargetToggle"
          size="sm"
          toggled={targetValue.active}
          onToggle={(isActive) => {
            onChange({
              targetValue: {
                active: {$set: isActive},
                isKpi: {$set: isActive && isSingleProcessReport},
              },
            });
          }}
          labelText={t('report.config.goal.legend')}
          hideLabel
        />
      }
    >
      <Stack gap={4}>
        <TargetSelection report={report} onChange={onChange} />
        {view.entity !== 'variable' && isSingleProcessReport && (
          <Checkbox
            id="setKpiCheckbox"
            labelText={t('report.config.goal.setKpi')}
            disabled={!targetValue.active}
            checked={!targetValue.active || targetValue.isKpi}
            onChange={(_evt, {checked}) => {
              onChange({targetValue: {isKpi: {$set: checked}}});
              trackKpiState(checked, report.id);
            }}
            helperText={t('report.config.goal.kpiDescription')}
          />
        )}
      </Stack>
    </FormGroup>
  );
}

function trackKpiState(isEnabled, reportId) {
  track('displayAsProcessKpi' + (isEnabled ? 'Enabled' : 'Disabled'), {
    entityId: reportId,
  });
}
