/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {Title} from 'modules/components/FiltersPanel/styled';
import {ComboBox} from 'modules/components/ComboBox';
import {Dropdown, Stack} from '@carbon/react';
import {useAvailableTenants} from 'modules/queries/useAvailableTenants';

const DecisionsFormGroup: React.FC = observer(() => {
  const {getVersions, getDefaultVersion, decisions} = groupedDecisionsStore;

  const form = useForm();
  const selectedDecisionKey = useField('name').input.value;
  const selectedTenant = useField('tenant').input.value;
  const versions = getVersions(selectedDecisionKey);
  const initialItems = versions.length > 1 ? ['all'] : [];
  const items = [...initialItems, ...versions.sort((a, b) => b - a)];
  const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;
  const isSpecificTenantSelected =
    selectedTenant !== '' && selectedTenant !== 'all';
  const tenantsById = useAvailableTenants();

  return (
    <div>
      <Title>Decision</Title>
      <Stack gap={5}>
        <Field name="name">
          {({input}) => {
            return (
              <ComboBox
                id="decisionName"
                aria-label="Select a Decision"
                items={decisions.map(({id, label, tenantId}) => ({
                  label:
                    isMultiTenancyEnabled && !isSpecificTenantSelected
                      ? `${label} - ${tenantsById[tenantId]}`
                      : label,
                  id,
                }))}
                onChange={({selectedItem}) => {
                  const decisionKey = selectedItem?.id;

                  input.onChange(decisionKey);
                  form.change(
                    'version',
                    decisionKey === undefined
                      ? ''
                      : getDefaultVersion(decisionKey),
                  );

                  if (isMultiTenancyEnabled) {
                    const tenantId = decisions.find(
                      ({id}) => id === decisionKey,
                    )?.tenantId;

                    if (tenantId !== undefined) {
                      form.change('tenant', tenantId);
                    }
                  }
                }}
                titleText="Name"
                value={input.value}
                placeholder="Search by Decision Name"
                disabled={isMultiTenancyEnabled && selectedTenant === ''}
              />
            );
          }}
        </Field>
        <Field name="version">
          {({input}) => (
            <Dropdown
              label="Select a Decision Version"
              aria-label="Select a Decision Version"
              titleText="Version"
              id="decisionVersion"
              onChange={({selectedItem}) => {
                input.onChange(selectedItem);
              }}
              disabled={versions.length === 0}
              items={items}
              itemToString={(item) =>
                item === 'all' ? 'All versions' : item.toString()
              }
              selectedItem={input.value}
              size="sm"
            />
          )}
        </Field>
      </Stack>
    </div>
  );
});

export {DecisionsFormGroup};
