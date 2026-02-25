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
  const processValue = useField('process').input.value;
  const {definitionId, tenantId} = splitDefinitionIdentifier(processValue);

  const {data: versions = []} = useProcessDefinitionVersions(
    definitionId,
    isMultiTenancyEnabled ? tenantId : undefined,
  );

  const isDisabled =
    batchModificationStore.state.isEnabled || versions.length === 0;

  return (
    <Field name="version">
      {({input}) => {
        return (
          <Dropdown
            label="Select a Process Version"
            aria-label="Select a Process Version"
            titleText="Version"
            id="processVersion"
            onChange={({selectedItem}) => {
              input.onChange(selectedItem);
              form.change('flowNodeId', undefined);
            }}
            disabled={isDisabled}
            items={versions}
            itemToString={(item) =>
              item === 'all' || item === null ? 'All versions' : item.toString()
            }
            selectedItem={input.value === 'all' ? 'all' : Number(input.value)}
            size="sm"
          />
        );
      }}
    </Field>
  );
});

export {ProcessVersionField};
