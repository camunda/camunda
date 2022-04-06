/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {EndDateField} from './styled';
import {validateDateCharacters, validateDateComplete} from 'modules/validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {Field} from 'react-final-form';
import {OptionalFilter} from './OptionalFilter';

const EndDate: React.FC = () => {
  return (
    <OptionalFilter name="endDate" filterList={['endDate']}>
      <Field
        name="endDate"
        validate={mergeValidators(validateDateCharacters, validateDateComplete)}
      >
        {({input}) => (
          <EndDateField
            {...input}
            type="text"
            data-testid="filter-end-date"
            label="End Date"
            placeholder="YYYY-MM-DD hh:mm:ss"
            shouldDebounceError={false}
            autoFocus
          />
        )}
      </Field>
    </OptionalFilter>
  );
};

export {EndDate};
