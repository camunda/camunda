/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {forwardRef} from 'react';
import {Field} from 'react-final-form';
import {DatePickerInput, TextInput} from '@carbon/react';
import {Container} from './styled';

type Props = {
  type: 'from' | 'to';
  id: string;
  labelText: string;
  autoFocus?: boolean;
};

const DateTimeInput = forwardRef<DatePickerInput, Props>(
  ({type, ...props}, ref) => {
    return (
      <Container>
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
                onChange={(event) => input.onChange(event.target.value)}
                ref={ref}
                placeholder="YYYY-MM-DD"
                pattern={'\\d{4}-\\d{1,2}-\\d{1,2}'}
                defaultValue={input.value}
                maxLength={10}
              />
            );
          }}
        </Field>
        <Field name={`${type}Time`}>
          {({input}) => {
            return (
              <TextInput
                pattern={'\\d{1,2}:\\d{1,2}:\\d{1,2}'}
                defaultValue={input.value}
                id="time-picker"
                labelText=""
                size="sm"
                onChange={(event) => input.onChange(event.target.value)}
                placeholder="hh:mm:ss"
                data-testid={`${type}Time`}
                light
                maxLength={8}
              />
            );
          }}
        </Field>
      </Container>
    );
  }
);

export {DateTimeInput};
