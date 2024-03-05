/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Dropdown} from '@carbon/react';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {CurrentUser} from 'modules/types';

type Props = {
  initialSelectedItem?: CurrentUser['tenants'][0];
  onChange: (tenant: string) => void;
  className?: string;
};

const MultiTenancyDropdown: React.FC<Props> = ({
  onChange,
  initialSelectedItem,
  className,
}) => {
  const {data: currentUser} = useCurrentUser();
  const defaultTenant = currentUser?.tenants[0];

  return (
    <Dropdown<CurrentUser['tenants'][0]>
      key={`tenant-dropdown-${currentUser?.tenants.length ?? 0}`}
      id="tenantId"
      items={currentUser?.tenants ?? []}
      itemToString={(item) => (item ? `${item.name} - ${item.id}` : '')}
      label="Tenant"
      titleText="Tenant"
      initialSelectedItem={initialSelectedItem ?? defaultTenant}
      onChange={(event) => {
        const id = event.selectedItem?.id;

        if (!id) {
          return;
        }

        onChange(id);
      }}
      className={className}
    />
  );
};

export {MultiTenancyDropdown};
