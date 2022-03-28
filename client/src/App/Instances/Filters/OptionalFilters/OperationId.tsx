/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {OperationIdField} from './styled';
import {
  validateOperationIdCharacters,
  validateOperationIdComplete,
} from 'modules/validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {Field} from 'react-final-form';
import {OptionalFilter} from './OptionalFilter';

const OperationId: React.FC = () => {
  return (
    <OptionalFilter name="operationId" filterList={['operationId']}>
      <Field
        name="operationId"
        validate={mergeValidators(
          validateOperationIdCharacters,
          validateOperationIdComplete
        )}
      >
        {({input}) => (
          <OperationIdField
            {...input}
            type="text"
            data-testid="filter-operation-id"
            label="Operation Id"
            shouldDebounceError={false}
            autoFocus
          />
        )}
      </Field>
    </OptionalFilter>
  );
};

export {OperationId};
