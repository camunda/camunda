/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
        validateTimeRange,
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
