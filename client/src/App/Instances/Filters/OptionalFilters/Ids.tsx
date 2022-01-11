/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {TextField} from 'modules/components/TextField';
import {
  validateIdsCharacters,
  validateIdsNotTooLong,
  validatesIdsComplete,
} from '../validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {Field} from 'react-final-form';
import {OptionalFilter} from './OptionalFilter';

const Ids: React.FC = () => {
  return (
    <OptionalFilter name="ids" filterList={['ids']}>
      <Field
        name="ids"
        validate={mergeValidators(
          validateIdsCharacters,
          validateIdsNotTooLong,
          validatesIdsComplete
        )}
      >
        {({input}) => (
          <TextField
            {...input}
            type="multiline"
            data-testid="filter-instance-ids"
            label="Instance Id(s)"
            placeholder="separated by space or comma"
            rows={1}
            shouldDebounceError={false}
          />
        )}
      </Field>
    </OptionalFilter>
  );
};

export {Ids};
