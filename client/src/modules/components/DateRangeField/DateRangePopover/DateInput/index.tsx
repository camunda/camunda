/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {forwardRef} from 'react';
import {Field} from 'react-final-form';
import {DatePickerInput} from '@carbon/react';
import {Container} from './styled';

type Props = {
  type: 'from' | 'to';
  id: string;
  labelText: string;
  autoFocus?: boolean;
};

const DateInput = forwardRef<DatePickerInput, Props>(
  ({type, ...props}, ref) => {
    return (
      <Container>
        <Field name={`${type}Date`}>
          {({input}) => (
            <DatePickerInput
              {...props}
              size="sm"
              ref={ref}
              placeholder="YYYY-MM-DD"
              pattern={'\\d{4}-\\d{1,2}-\\d{1,2}'}
              defaultValue={input.value}
            />
          )}
        </Field>
      </Container>
    );
  }
);

export {DateInput};
