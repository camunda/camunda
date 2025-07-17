/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Field} from 'react-final-form';
import {observer} from 'mobx-react';
import {Dropdown} from '@carbon/react';
import {useCurrentUser} from 'modules/queries/useCurrentUser';

type Props = {
  onChange?: (selectedItem: string) => void;
};

const TenantField: React.FC<Props> = observer(({onChange}) => {
  const {data: currentUser} = useCurrentUser();
  const tenants = currentUser?.tenants;
  const tenantsById: Record<string, string> =
    tenants?.reduce(
      (acc, tenant) => ({
        [tenant.tenantId]: tenant.name,
        ...acc,
      }),
      {},
    ) ?? {};
  const items = ['all', ...(tenants?.map(({tenantId}) => tenantId) ?? [])];

  return (
    <Field name="tenant">
      {({input}) => {
        return (
          <Dropdown
            label="Select a tenant"
            aria-label="Select a tenant"
            titleText="Tenant"
            hideLabel
            id="tenant"
            onChange={({selectedItem}) => {
              input.onChange(selectedItem);
              onChange?.(selectedItem);
            }}
            items={items}
            itemToString={(item: string) => {
              return item === 'all'
                ? 'All tenants'
                : (tenantsById[item] ?? item);
            }}
            selectedItem={items.includes(input.value) ? input.value : ''}
            size="sm"
          />
        );
      }}
    </Field>
  );
});

export {TenantField};
