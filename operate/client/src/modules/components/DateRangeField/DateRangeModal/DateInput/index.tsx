/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

const DateInput = forwardRef<HTMLDivElement, Props>(
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
              // @ts-expect-error - Carbon types are wrong
              pattern="\\d{4}-\\d{1,2}-\\d{1,2}"
              value={input.value}
              maxLength={10}
              autoComplete="off"
            />
          );
        }}
      </Field>
    );
  },
);

export {DateInput};
