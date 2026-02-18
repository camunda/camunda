/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, useField, useForm} from 'react-final-form';
import {observer} from 'mobx-react';
import {Title} from 'modules/components/FiltersPanel/styled';
import {ComboBox} from 'modules/components/ComboBox';
import {Dropdown, Stack} from '@carbon/react';
import {useAvailableTenants} from 'modules/queries/useAvailableTenants';
import {
  getDefinitionIdFromIdentifier,
  useDecisionDefinitions,
  useDecisionDefinitionVersions,
} from 'modules/hooks/decisionDefinitions';

const DecisionsFormGroup: React.FC = observer(() => {
  const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;
  const tenantsById = useAvailableTenants();

  const form = useForm();
  const nameValue = useField('name').input.value;
  const tenantValue = useField('tenant').input.value;

  const definitionId = getDefinitionIdFromIdentifier(nameValue);
  const specificTenantId =
    isMultiTenancyEnabled && tenantValue !== '' && tenantValue !== 'all'
      ? tenantValue
      : undefined;

  const {data: definitions = []} = useDecisionDefinitions(specificTenantId);
  const {data: versions = []} = useDecisionDefinitionVersions(
    definitionId,
    specificTenantId,
  );

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
                items={definitions.map((definition) => ({
                  id: definition.identifier,
                  label:
                    isMultiTenancyEnabled && !specificTenantId
                      ? `${definition.name} - ${tenantsById[definition.tenantId]}`
                      : definition.name,
                }))}
                onChange={({selectedItem}) => {
                  const matchingDecision = definitions.find(
                    (d) => d.identifier === selectedItem?.id,
                  );
                  input.onChange(selectedItem?.id);
                  form.change('version', matchingDecision?.version ?? '');
                  if (isMultiTenancyEnabled && matchingDecision) {
                    form.change('tenant', matchingDecision.tenantId);
                  }
                }}
                titleText="Name"
                value={input.value}
                placeholder="Search by Decision Name"
                disabled={isMultiTenancyEnabled && tenantValue === ''}
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
              items={versions}
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
