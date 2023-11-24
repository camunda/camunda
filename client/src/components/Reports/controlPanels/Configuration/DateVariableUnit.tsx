/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FormGroup} from '@carbon/react';

import {CarbonSelect} from 'components';
import {t} from 'translation';

interface DateVariableUnitProps {
  configuration: Record<string, unknown>;
  groupBy?: {value: {type?: string}; type: string};
  distributedBy?: {value: {type: string}; type: string};
  onChange: (change: Record<string, {$set: string}>, needsReevaluation?: boolean) => void;
}

export default function DateVariableUnit({
  configuration,
  groupBy,
  distributedBy,
  onChange,
}: DateVariableUnitProps) {
  const isDistributedByVariable =
    distributedBy?.type === 'variable' && ['Date'].includes(distributedBy.value.type);
  const groupByDateVariableUnit = isDistributedByVariable
    ? 'distributeByDateVariableUnit'
    : 'groupByDateVariableUnit';

  const isGroupedByDateVariable =
    groupBy?.type.toLowerCase().includes('variable') && groupBy.value?.type === 'Date';

  if (isGroupedByDateVariable || isDistributedByVariable) {
    return (
      <FormGroup className="DateVariableUnit" legendText={t('report.config.bucket.buckets')}>
        <CarbonSelect
          id="dateVariableUnit"
          value={configuration[groupByDateVariableUnit] as string}
          onChange={(value) => onChange({[groupByDateVariableUnit]: {$set: value}}, true)}
        >
          <CarbonSelect.Option value="automatic" label={t('common.unit.automatic')} />
          <CarbonSelect.Option value="hour" label={t('common.unit.hour.label-plural')} />
          <CarbonSelect.Option value="day" label={t('common.unit.day.label-plural')} />
          <CarbonSelect.Option value="week" label={t('common.unit.week.label-plural')} />
          <CarbonSelect.Option value="month" label={t('common.unit.month.label-plural')} />
          <CarbonSelect.Option value="year" label={t('common.unit.year.label-plural')} />
        </CarbonSelect>
      </FormGroup>
    );
  }
  return null;
}
