/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Popover, ButtonGroup, Button, Switch, Form} from 'components';

import './TenantPopover.scss';
import {t} from 'translation';

export default function TenantPopover({tenants, selected, onChange}) {
  const allSelected = tenants.length === selected.length;
  const noneSelected = selected.length === 0;

  let label = t('common.definitionSelection.multiple');
  if (allSelected) {
    label = t('common.all');
  }
  if (noneSelected) {
    label = t('common.select');
  }
  if (selected.length === 1 && tenants.length !== 0) {
    label = tenants.find(({id}) => id === selected[0]).name;
  }

  return (
    <Popover className="TenantPopover" disabled={tenants.length < 2} title={label}>
      <Form compact>
        <fieldset>
          <legend>{t('common.definitionSelection.tenant.includeData')}</legend>
          <ButtonGroup>
            <Button onClick={() => onChange(tenants.map(({id}) => id))}>
              {t('common.enableAll')}
            </Button>
            <Button onClick={() => onChange([])}>{t('common.disableAll')}</Button>
          </ButtonGroup>
          {tenants.map(tenant => (
            <div key={tenant.id}>
              <Switch
                checked={selected.includes(tenant.id)}
                onChange={({target}) => {
                  if (target.checked) {
                    onChange(selected.concat([tenant.id]));
                  } else {
                    onChange(selected.filter(id => id !== tenant.id));
                  }
                }}
              />
              {tenant.name}
            </div>
          ))}
        </fieldset>
      </Form>
    </Popover>
  );
}
