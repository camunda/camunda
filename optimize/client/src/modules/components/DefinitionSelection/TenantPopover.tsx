/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentProps, ReactNode} from 'react';
import {Button, ButtonSet, Form, FormGroup, Loading, Stack, Toggle} from '@carbon/react';

import {t} from 'translation';
import {formatters} from 'services';
import {Popover} from 'components';
import {Tenant} from 'types';

import './TenantPopover.scss';

const {formatTenantName} = formatters;

interface TenantPopoverProps extends Pick<ComponentProps<typeof Popover>, 'floating' | 'align'> {
  loading?: boolean;
  tenants: Tenant[];
  selected: Tenant['id'][];
  disabled?: boolean;
  onChange: (tenants: Tenant['id'][]) => void;
  label?: ReactNode;
}

export default function TenantPopover({
  loading,
  tenants,
  selected,
  disabled,
  onChange,
  floating,
  align,
  label,
}: TenantPopoverProps) {
  const allSelected = tenants && tenants.length === selected.length;
  const noneSelected = selected.length === 0;

  let value: string | undefined = t('common.definitionSelection.multiple').toString();
  if (allSelected) {
    value = t('common.all').toString();
  }
  if (noneSelected) {
    value = t('common.select').toString();
  }
  if (selected?.length === 1 && tenants?.length !== 0) {
    value = tenants?.find(({id}) => id === selected[0])?.name;
  }

  return (
    <Popover
      className="TenantPopover"
      trigger={
        <Popover.ListBox label={label} disabled={disabled || tenants?.length < 2 || !tenants}>
          {value || '-'}
        </Popover.ListBox>
      }
      floating={floating}
      align={align}
    >
      {loading && <Loading withOverlay />}
      <Form>
        <FormGroup legendText={t('common.definitionSelection.tenant.includeData')}>
          <Stack gap={6}>
            <ButtonSet aria-disabled={loading}>
              <Button
                disabled={loading}
                size="sm"
                kind="primary"
                onClick={() => onChange(tenants.map(({id}) => id))}
              >
                {t('common.enableAll')}
              </Button>
              <Button disabled={loading} size="sm" kind="secondary" onClick={() => onChange([])}>
                {t('common.disableAll')}
              </Button>
            </ButtonSet>
            <Stack gap={3}>
              {tenants?.map((tenant) => {
                return (
                  <Toggle
                    key={tenant.id}
                    id={`toggle-${tenant.id}`}
                    toggled={selected.includes(tenant.id)}
                    disabled={loading}
                    size="sm"
                    labelText={formatTenantName(tenant).toString()}
                    hideLabel
                    onToggle={(checked) => {
                      if (checked) {
                        onChange(selected.concat([tenant.id]));
                      } else {
                        onChange(selected.filter((id) => id !== tenant.id));
                      }
                    }}
                  />
                );
              })}
            </Stack>
          </Stack>
        </FormGroup>
      </Form>
    </Popover>
  );
}
