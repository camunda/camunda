/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';
import {Dropdown} from '@carbon/react';
import {batchModificationStore} from 'modules/stores/batchModification';
import {
  splitDefinitionIdentifier,
  useProcessDefinitionVersions,
} from 'modules/hooks/processDefinitions';
import {getClientConfig} from 'modules/utils/getClientConfig';

const ProcessVersionField: React.FC = observer(() => {
  const isMultiTenancyEnabled = getClientConfig().multiTenancyEnabled;
  const form = useForm();
  const processValue = useField('processDefinitionId').input.value;
  const {definitionId, tenantId} = splitDefinitionIdentifier(processValue);

  const {data: versions = []} = useProcessDefinitionVersions(
    definitionId,
    isMultiTenancyEnabled ? tenantId : undefined,
  );

  const isDisabled =
    batchModificationStore.state.isEnabled || versions.length === 0;

  return (
    <Field name="processDefinitionVersion">
      {({input}) => {
        const inputValue = input.value === 'all' ? 'all' : Number(input.value);
        const selectedItem =
          versions.find(({value}) => value === inputValue) ??
          (input.value === ''
            ? null
            : {
                value: inputValue,
              });

        return (
          <Dropdown
            label="Select a Process Version"
            aria-label="Select a Process Version"
            titleText="Version"
            id="processVersion"
            onChange={({selectedItem}) => {
              input.onChange(selectedItem?.value);
              form.change('elementId', undefined);
            }}
            disabled={isDisabled}
            items={versions}
            itemToString={(item) => {
              if (item === null) {
                return '';
              }
              if (item.value === 'all') {
                return 'All versions';
              }
              return item.state === 'DELETED'
                ? `${item.value} (Deleted)`
                : item.value.toString();
            }}
            selectedItem={selectedItem}
            size="sm"
          />
        );
      }}
    </Field>
  );
});

export {ProcessVersionField};
