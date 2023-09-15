/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentProps} from 'react';
import {Button, ButtonSet, Form, FormGroup, Loading, Stack, Toggle} from '@carbon/react';

import {t} from 'translation';
import {formatters} from 'services';
import {Popover} from 'components';
import {Tenant} from 'types';

import './TenantPopover.scss';

const {formatTenantName} = formatters;

interface TenantPopoverProps extends Omit<ComponentProps<typeof Popover>, 'onChange' | 'children'> {
  loading?: boolean;
  tenants: Tenant[];
  selected: Tenant['id'][];
  disabled?: boolean;
  onChange: (tenants: Tenant['id'][]) => void;
  useCarbonTrigger?: boolean;
}

export default function TenantPopover({
  loading,
  tenants,
  selected,
  disabled,
  onChange,
  useCarbonTrigger,
  ...props
}: TenantPopoverProps) {
  const allSelected = tenants && tenants.length === selected.length;
  const noneSelected = selected.length === 0;

  let label: string | undefined = t('common.definitionSelection.multiple').toString();
  if (allSelected) {
    label = t('common.all').toString();
  }
  if (noneSelected) {
    label = t('common.select').toString();
  }
  if (selected?.length === 1 && tenants?.length !== 0) {
    label = tenants?.find(({id}) => id === selected[0])?.name;
  }

  return (
    <Popover
      className="TenantPopover"
      disabled={disabled || tenants?.length < 2 || !tenants}
      title={label || '-'}
      trigger={
        useCarbonTrigger ? (
          <Popover.ListBox label={label} disabled={disabled || tenants?.length < 2 || !tenants}>
            {label || '-'}
          </Popover.ListBox>
        ) : undefined
      }
      {...props}
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
                    labelA={formatTenantName(tenant).toString()}
                    labelB={formatTenantName(tenant).toString()}
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
