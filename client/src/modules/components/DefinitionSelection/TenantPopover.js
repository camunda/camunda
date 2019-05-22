/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Popover, ButtonGroup, Button, Switch} from 'components';

import './TenantPopover.scss';

export default function TenantPopover({tenants, selected, onChange}) {
  const allSelected = tenants.length === selected.length;
  const noneSelected = selected.length === 0;

  let label = 'Multiple';
  if (allSelected) {
    label = 'All';
  }
  if (noneSelected) {
    label = 'Select...';
  }
  if (selected.length === 1) {
    label = tenants.find(({id}) => id === selected[0]).name;
  }

  return (
    <Popover className="TenantPopover" title={label}>
      <ButtonGroup>
        <Button
          color={allSelected ? 'green' : undefined}
          onClick={() => onChange(tenants.map(({id}) => id))}
        >
          Enable All
        </Button>
        <Button color={noneSelected ? 'green' : undefined} onClick={() => onChange([])}>
          Disable All
        </Button>
      </ButtonGroup>
      <fieldset>
        <legend>Include data from</legend>
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
    </Popover>
  );
}
