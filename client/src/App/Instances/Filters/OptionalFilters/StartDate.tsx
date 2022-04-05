/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {StartDateField} from './styled';
import {validateDateCharacters, validateDateComplete} from 'modules/validators';
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
          <StartDateField
            {...input}
            type="text"
            data-testid="filter-start-date"
            label="Start Date"
            placeholder="YYYY-MM-DD hh:mm:ss"
            shouldDebounceError={false}
            autoFocus
          />
        )}
      </Field>
    </OptionalFilter>
  );
};

export {StartDate};
