/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ErrorMessageField} from './styled';
import {Field} from 'react-final-form';
import {OptionalFilter} from './OptionalFilter';

const ErrorMessage: React.FC = () => {
  return (
    <OptionalFilter name="errorMessage" filterList={['errorMessage']}>
      <Field name="errorMessage">
        {({input}) => (
          <ErrorMessageField
            {...input}
            type="text"
            data-testid="filter-error-message"
            label="Error Message"
            shouldDebounceError={false}
            autoFocus
          />
        )}
      </Field>
    </OptionalFilter>
  );
};

export {ErrorMessage};
