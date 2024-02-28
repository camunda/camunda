/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Field} from 'react-final-form';
import {observer} from 'mobx-react';
import {Dropdown} from '@carbon/react';
import {authenticationStore} from 'modules/stores/authentication';

type Props = {
  onChange?: (selectedItem: string) => void;
};

const TenantField: React.FC<Props> = observer(({onChange}) => {
  const {
    state: {tenants},
    tenantsById,
  } = authenticationStore;
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
                : tenantsById?.[item] ?? item;
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
