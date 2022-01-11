/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {TextField} from 'modules/components/TextField';
import {validateDateCharacters, validateDateComplete} from '../validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {Field} from 'react-final-form';
import {OptionalFilter} from './OptionalFilter';

const StartDate: React.FC = () => {
  return (
    <OptionalFilter name="startDate" filterList={['startDate']}>
      <Field
        name="startDate"
        validate={mergeValidators(validateDateCharacters, validateDateComplete)}
      >
        {({input}) => (
          <TextField
            {...input}
            type="text"
            data-testid="filter-start-date"
            label="Start Date"
            placeholder="YYYY-MM-DD hh:mm:ss"
            shouldDebounceError={false}
          />
        )}
      </Field>
    </OptionalFilter>
  );
};

export {StartDate};
