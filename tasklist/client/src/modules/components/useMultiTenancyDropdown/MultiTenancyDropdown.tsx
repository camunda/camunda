/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Dropdown} from '@carbon/react';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {CurrentUser} from 'modules/types';
import {useTranslation} from 'react-i18next';

type Tenants = CurrentUser['tenants'][0];

type Props = {
  initialSelectedItem?: Tenants;
  onChange: (tenant: string) => void;
  className?: string;
} & Omit<
  React.ComponentProps<typeof Dropdown<Tenants>>,
  | 'id'
  | 'items'
  | 'itemToString'
  | 'label'
  | 'titleText'
  | 'initialSelectedItem'
  | 'onChange'
>;

const MultiTenancyDropdown: React.FC<Props> = ({
  onChange,
  initialSelectedItem,
  hideLabel = true,
  ...props
}) => {
  const {data: currentUser} = useCurrentUser();
  const {t} = useTranslation();
  const defaultTenant = currentUser?.tenants[0];

  return (
    <Dropdown<Tenants>
      {...props}
      key={`tenant-dropdown-${currentUser?.tenants.length ?? 0}`}
      id="tenantId"
      items={currentUser?.tenants ?? []}
      itemToString={(item) => (item ? `${item.name} - ${item.id}` : '')}
      label={t('tenantLabel')}
      hideLabel={hideLabel}
      titleText={t('tenantTitle')}
      initialSelectedItem={initialSelectedItem ?? defaultTenant}
      onChange={(event) => {
        const id = event.selectedItem?.id;

        if (!id) {
          return;
        }

        onChange(id);
      }}
    />
  );
};

export {MultiTenancyDropdown};
