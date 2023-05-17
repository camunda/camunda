/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {forwardRef} from 'react';
import {Field} from 'react-final-form';
import {DatePickerInput} from '@carbon/react';

type Props = {
  type: 'from' | 'to';
  id: string;
  labelText: string;
  onChange?: () => void;
  autoFocus?: boolean;
};

const DateInput = forwardRef<DatePickerInput, Props>(
  ({type, onChange, ...props}, ref) => {
    return (
      <Field name={`${type}Date`}>
        {({
          input,
        }: {
          input: {value: string; onChange: (value: string) => void};
        }) => {
          return (
            <DatePickerInput
              {...props}
              size="sm"
              onChange={(event) => {
                input.onChange(event.target.value);
                onChange?.();
              }}
              ref={ref}
              placeholder="YYYY-MM-DD"
              pattern={'\\d{4}-\\d{1,2}-\\d{1,2}'}
              defaultValue={input.value}
              maxLength={10}
              autoComplete="off"
            />
          );
        }}
      </Field>
    );
  }
);

export {DateInput};
