/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Checkbox, FormGroup, Stack, Toggle} from '@carbon/react';

import {t} from 'translation';
import {track} from 'tracking';

import CountTargetInput from './subComponents/CountTargetInput';
import DurationTargetInput from './subComponents/DurationTargetInput';

export default function NumberConfig({report, onChange}) {
  const {configuration, view, definitions} = report.data;
  const targetValue = configuration.targetValue;
  const isPercentageReport = view.properties.includes('percentage');

  const countOperation =
    view.properties.includes('frequency') || isPercentageReport || view.entity === 'variable';
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
          labelA={t('report.config.goal.legend')}
          labelB={t('report.config.goal.legend')}
        />
      }
    >
      <Stack gap={4}>
        {countOperation ? (
          <CountTargetInput
            baseline={targetValue.countProgress.baseline}
            target={targetValue.countProgress.target}
            isBelow={targetValue.countProgress.isBelow}
            disabled={!targetValue.active}
            isPercentageReport={isPercentageReport}
            onChange={(type, value) =>
              onChange({targetValue: {countProgress: {[type]: {$set: value}}}})
            }
          />
        ) : (
          <DurationTargetInput
            baseline={targetValue.durationProgress.baseline}
            target={targetValue.durationProgress.target}
            disabled={!targetValue.active}
            onChange={(type, subType, value) =>
              onChange({targetValue: {durationProgress: {[type]: {[subType]: {$set: value}}}}})
            }
          />
        )}
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
