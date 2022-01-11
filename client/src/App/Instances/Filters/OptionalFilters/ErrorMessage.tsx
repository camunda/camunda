/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {TextField} from 'modules/components/TextField';
import {Field} from 'react-final-form';
import {OptionalFilter} from './OptionalFilter';

const ErrorMessage: React.FC = () => {
  return (
    <OptionalFilter name="errorMessage" filterList={['errorMessage']}>
      <Field name="errorMessage">
        {({input}) => (
          <TextField
            {...input}
            type="text"
            data-testid="filter-error-message"
            label="Error Message"
            shouldDebounceError={false}
          />
        )}
      </Field>
    </OptionalFilter>
  );
};

export {ErrorMessage};
