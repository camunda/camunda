/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MenuItemSelectable} from '@carbon/react';

import {MenuDropdown} from 'components';
import {t} from 'translation';

import './FilterBar.scss';

export const DATE_PRESETS = [
  {id: '7d', label: 'Last 7 days', value: 7, unit: 'days'},
  {id: '30d', label: 'Last 30 days', value: 30, unit: 'days'},
  {id: '3m', label: 'Last 3 months', value: 3, unit: 'months'},
  {id: '6m', label: 'Last 6 months', value: 6, unit: 'months'},
  {id: '12m', label: 'Last 12 months', value: 12, unit: 'months'},
];

export function FilterBar({preset, onPresetChange}) {
  const selected = DATE_PRESETS.find((p) => p.id === preset);

  return (
    <div className="FilterBar">
      <div className="FilterBar__dateRange">
        <span className="FilterBar__label">
          {t('agenticControlPlane.dateRange') || 'Date range'}
        </span>
        <MenuDropdown label={selected.label} size="sm">
          {DATE_PRESETS.map((p) => (
            <MenuItemSelectable
              key={p.id}
              label={p.label}
              selected={p.id === preset}
              onChange={() => onPresetChange(p.id)}
            />
          ))}
        </MenuDropdown>
      </div>
    </div>
  );
}
