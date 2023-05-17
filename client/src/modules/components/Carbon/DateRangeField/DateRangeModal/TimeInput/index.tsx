/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Field} from 'react-final-form';
import {TextInput} from '@carbon/react';
import {
  validateTimeCharacters,
  validateTimeComplete,
  validateTimeRange,
} from 'modules/validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';

type Props = {
  type: 'from' | 'to';
  onChange?: () => void;
  labelText: string;
};

const TimeInput: React.FC<Props> = ({type, labelText}) => {
  return (
    <Field
      name={`${type}Time`}
      validate={mergeValidators(
        validateTimeComplete,
        validateTimeCharacters,
        validateTimeRange
      )}
    >
      {({input, onChange, meta}) => {
        return (
          <TextInput
            defaultValue={input.value}
            id="time-picker"
            labelText={labelText}
            size="sm"
            onChange={(event) => {
              input.onChange(event.target.value);
              onChange?.();
            }}
            placeholder="hh:mm:ss"
            data-testid={`${type}Time`}
            maxLength={8}
            autoComplete="off"
            invalid={meta.invalid}
            invalidText={meta.error}
          />
        );
      }}
    </Field>
  );
};

export {TimeInput};
