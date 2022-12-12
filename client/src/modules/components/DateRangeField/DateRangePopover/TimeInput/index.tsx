/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Field} from 'react-final-form';
import {TextInput} from '@carbon/react';

type Props = {
  type: 'from' | 'to';
};

const TimeInput: React.FC<Props> = ({type}) => {
  return (
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
  );
};

export {TimeInput};
