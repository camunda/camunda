/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {t} from 'translation';
import {formatters} from 'services';
import {CarbonPopover, ButtonGroup, Button, Switch, Form, LoadingIndicator} from 'components';

import './TenantPopover.scss';

const {formatTenantName} = formatters;

export default function TenantPopover({loading, tenants, selected, disabled, onChange, ...props}) {
  const allSelected = tenants && tenants.length === selected.length;
  const noneSelected = selected.length === 0;

  let label = t('common.definitionSelection.multiple');
  if (allSelected) {
    label = t('common.all');
  }
  if (noneSelected) {
    label = t('common.select');
  }
  if (selected?.length === 1 && tenants?.length !== 0) {
    label = tenants?.find(({id}) => id === selected[0])?.name;
  }

  return (
    <CarbonPopover
      className="TenantPopover"
      disabled={disabled || tenants?.length < 2 || !tenants}
      title={label || '-'}
      {...props}
    >
      {loading && <LoadingIndicator />}
      <Form compact>
        <fieldset>
          <legend>{t('common.definitionSelection.tenant.includeData')}</legend>
          <ButtonGroup disabled={loading}>
            <Button onClick={() => onChange(tenants.map(({id}) => id))}>
              {t('common.enableAll')}
            </Button>
            <Button onClick={() => onChange([])}>{t('common.disableAll')}</Button>
          </ButtonGroup>
          {tenants?.map((tenant) => {
            return (
              <Switch
                key={tenant.id}
                checked={selected.includes(tenant.id)}
                disabled={loading}
                onChange={({target}) => {
                  if (target.checked) {
                    onChange(selected.concat([tenant.id]));
                  } else {
                    onChange(selected.filter((id) => id !== tenant.id));
                  }
                }}
                label={formatTenantName(tenant)}
              />
            );
          })}
        </fieldset>
      </Form>
    </CarbonPopover>
  );
}
