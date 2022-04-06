/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ParentInstanceIdField} from './styled';
import {
  validateParentInstanceIdComplete,
  validateParentInstanceIdNotTooLong,
  validateParentInstanceIdCharacters,
} from 'modules/validators';
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
          <ParentInstanceIdField
            {...input}
            type="text"
            data-testid="filter-parent-instance-id"
            label="Parent Instance Id"
            shouldDebounceError={false}
            autoFocus
          />
        )}
      </Field>
    </OptionalFilter>
  );
};

export {ParentInstanceIds};
