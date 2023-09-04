/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentProps} from 'react';
import {TextInput} from '@carbon/react';

import {CarbonSelect} from 'components';
import {t} from 'translation';
import {getRandomId} from 'services';

import './ThresholdInput.scss';

interface CommonProps
  extends Pick<
    ComponentProps<typeof TextInput>,
    'className' | 'labelText' | 'invalid' | 'invalidText'
  > {
  type: string;
}

interface SingleValue extends CommonProps {
  value: string | number | undefined;
  onChange: (value: string | number | undefined) => void;
}

interface DurationValue extends CommonProps {
  value: {value: string | number | undefined; unit: string};
  onChange: (value: {value: string | number | undefined; unit: string}) => void;
}

type ThresholdInputProps = SingleValue | DurationValue;

export default function ThresholdInput(props: ThresholdInputProps) {
  const id = getRandomId();
  if (isDurationValue(props)) {
    const {value, type, onChange, ...rest} = props;
    return (
      <>
        <TextInput
          {...rest}
          id={`${id}Value`}
          size="sm"
          value={value?.value}
          onChange={({target}) => onChange({...value, value: target.value})}
          maxLength={8}
        />
        <CarbonSelect
          id={`${id}Units`}
          labelText={t('common.units')}
          className={rest.className}
          value={value.unit}
          onChange={(unit) => onChange({...value, unit})}
        >
          <CarbonSelect.Option value="millis" label={t('common.unit.milli.label-plural')} />
          <CarbonSelect.Option value="seconds" label={t('common.unit.second.label-plural')} />
          <CarbonSelect.Option value="minutes" label={t('common.unit.minute.label-plural')} />
          <CarbonSelect.Option value="hours" label={t('common.unit.hour.label-plural')} />
          <CarbonSelect.Option value="days" label={t('common.unit.day.label-plural')} />
          <CarbonSelect.Option value="weeks" label={t('common.unit.week.label-plural')} />
          <CarbonSelect.Option value="months" label={t('common.unit.month.label-plural')} />
        </CarbonSelect>
      </>
    );
  } else {
    const {value, type, onChange, ...rest} = props;
    return (
      <div className="ThresholdInput">
        <TextInput
          {...rest}
          id={`${id}Value`}
          size="sm"
          value={value}
          onChange={({target: {value}}) => onChange(value)}
        />
        {type === 'percentage' && <span className="percentageIndicator">%</span>}
      </div>
    );
  }
}

function isDurationValue(props: ThresholdInputProps): props is DurationValue {
  return props.type === 'duration';
}
