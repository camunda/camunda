/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
        {view.entity !== 'variable' &&
          report.reportType !== 'decision' &&
          isSingleProcessReport && (
            <Checkbox
              id="setKpiCheckbox"
              labelText={t('report.config.goal.setKpi')}
              disabled={!targetValue.active}
              checked={!targetValue.active || targetValue.isKpi}
              onChange={(evt, {checked}) => {
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
