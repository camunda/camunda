/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {TextField} from 'modules/components/TextField';
import {
  validateParentInstanceIdComplete,
  validateParentInstanceIdNotTooLong,
  validateParentInstanceIdCharacters,
} from '../validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {Field} from 'react-final-form';
import {OptionalFilter} from './OptionalFilter';

const ParentInstanceIds: React.FC = () => {
  return (
    <OptionalFilter name="parentInstanceId" filterList={['parentInstanceId']}>
      <Field
        name="parentInstanceId"
        validate={mergeValidators(
          validateParentInstanceIdComplete,
          validateParentInstanceIdNotTooLong,
          validateParentInstanceIdCharacters
        )}
      >
        {({input}) => (
          <TextField
            {...input}
            type="text"
            data-testid="filter-parent-instance-id"
            label="Parent Instance Id"
            shouldDebounceError={false}
          />
        )}
      </Field>
    </OptionalFilter>
  );
};

export {ParentInstanceIds};
