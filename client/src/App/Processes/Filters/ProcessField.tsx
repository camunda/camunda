/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';
import {processesStore} from 'modules/stores/processes';
import {ComboBox} from 'modules/components/ComboBox';
import {authenticationStore} from 'modules/stores/authentication';

const ProcessField: React.FC = observer(() => {
  const {processes, versionsByProcessAndTenant} = processesStore;
  const form = useForm();

  const selectedTenant = useField('tenant').input.value;
  const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;

  const isSpecificTenantSelected =
    selectedTenant !== '' && selectedTenant !== 'all';

  return (
    <Field name="process" data-testid="filter-process-name-field">
      {({input}) => (
        <ComboBox
          titleText="Name"
          id="processName"
          aria-label="Select a Process"
          placeholder="Search by Process Name"
          onChange={({selectedItem}) => {
            const versions = selectedItem
              ? versionsByProcessAndTenant[selectedItem.id]
              : [];
            const initialVersionSelection =
              versions === undefined
                ? undefined
                : versions[versions.length - 1]?.version;

            input.onChange(selectedItem?.id);

            form.change('version', initialVersionSelection);
            form.change('flowNodeId', undefined);

            if (isMultiTenancyEnabled) {
              const tenant = processes.find(({id}) => id === selectedItem?.id)
                ?.tenantId;

              if (tenant !== undefined) {
                form.change('tenant', tenant);
              }
            }
          }}
          items={processes.map(({id, label, tenantId}) => {
            return {
              label:
                isMultiTenancyEnabled && !isSpecificTenantSelected
                  ? `${label} - ${authenticationStore.tenantsById?.[tenantId]}`
                  : label,
              id,
            };
          })}
          value={input.value}
          disabled={isMultiTenancyEnabled && selectedTenant === ''}
        />
      )}
    </Field>
  );
});

export {ProcessField};
