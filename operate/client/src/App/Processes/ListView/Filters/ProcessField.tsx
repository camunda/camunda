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
import {ComboBox} from 'modules/components/ComboBox';
import {batchModificationStore} from 'modules/stores/batchModification';
import {useAvailableTenants} from 'modules/queries/useAvailableTenants';
import {useProcessDefinitions} from 'modules/hooks/processDefinitions';
import {getClientConfig} from 'modules/utils/getClientConfig';

const ProcessField: React.FC = observer(() => {
  const isMultiTenancyEnabled = getClientConfig().multiTenancyEnabled;
  const tenantsById = useAvailableTenants();

  const form = useForm();
  const tenantValue = useField('tenant').input.value;
  const specificTenantId =
    isMultiTenancyEnabled && tenantValue !== '' && tenantValue !== 'all'
      ? tenantValue
      : undefined;

  const {data: definitions = []} = useProcessDefinitions(specificTenantId);

  const isDisabled =
    (isMultiTenancyEnabled && tenantValue === '') ||
    batchModificationStore.state.isEnabled;

  return (
    <Field name="process" data-testid="filter-process-name-field">
      {({input}) => (
        <ComboBox
          titleText="Name"
          id="processName"
          aria-label="Select a Process"
          placeholder="Search by Process Name"
          onChange={({selectedItem}) => {
            if (selectedItem?.id !== input.value) {
              const matchingProcess = definitions.find(
                (d) => d.identifier === selectedItem?.id,
              );
              input.onChange(selectedItem?.id);
              form.change('version', matchingProcess?.version ?? '');
              form.change('flowNodeId', undefined);
              if (isMultiTenancyEnabled && matchingProcess) {
                form.change('tenant', matchingProcess.tenantId);
              }
            }
          }}
          items={definitions.map((definition) => {
            return {
              id: definition.identifier,
              label:
                isMultiTenancyEnabled && !specificTenantId
                  ? `${definition.label} - ${tenantsById[definition.tenantId]}`
                  : definition.label,
            };
          })}
          value={input.value}
          disabled={isDisabled}
          title={
            batchModificationStore.state.isEnabled
              ? 'Not changeable in batch modification mode'
              : undefined
          }
        />
      )}
    </Field>
  );
});

export {ProcessField};
