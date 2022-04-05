/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  validateIdsCharacters,
  validateIdsLength,
  validatesIdsComplete,
} from 'modules/validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {Field} from 'react-final-form';
import {OptionalFilter} from './OptionalFilter';
import {IdsField} from './styled';

const Ids: React.FC = () => {
  return (
    <OptionalFilter name="ids" filterList={['ids']}>
      <Field
        name="ids"
        validate={mergeValidators(
          validateIdsCharacters,
          validateIdsLength,
          validatesIdsComplete
        )}
      >
        {({input}) => (
          <IdsField
            {...input}
            type="multiline"
            data-testid="filter-instance-ids"
            label="Instance Id(s)"
            placeholder="separated by space or comma"
            rows={1}
            shouldDebounceError={false}
            autoFocus
          />
        )}
      </Field>
    </OptionalFilter>
  );
};

export {Ids};
