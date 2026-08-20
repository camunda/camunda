/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FormGroup} from '@carbon/react';

import {Select} from 'components';
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
        <Select
          id="dateVariableUnit"
          value={configuration[groupByDateVariableUnit] as string}
          onChange={(value) => onChange({[groupByDateVariableUnit]: {$set: value}}, true)}
        >
          <Select.Option value="automatic" label={t('common.unit.automatic')} />
          <Select.Option value="hour" label={t('common.unit.hour.label-plural')} />
          <Select.Option value="day" label={t('common.unit.day.label-plural')} />
          <Select.Option value="week" label={t('common.unit.week.label-plural')} />
          <Select.Option value="month" label={t('common.unit.month.label-plural')} />
          <Select.Option value="year" label={t('common.unit.year.label-plural')} />
        </Select>
      </FormGroup>
    );
  }
  return null;
}
