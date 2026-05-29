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
  {id: '7d', translationKey: 'agenticControlPlane.presets.last7Days', value: 7, unit: 'days'},
  {id: '30d', translationKey: 'agenticControlPlane.presets.last30Days', value: 30, unit: 'days'},
  {id: '3m', translationKey: 'agenticControlPlane.presets.last3Months', value: 3, unit: 'months'},
  {id: '6m', translationKey: 'agenticControlPlane.presets.last6Months', value: 6, unit: 'months'},
  {id: '12m', translationKey: 'agenticControlPlane.presets.last12Months', value: 12, unit: 'months'},
];

export function FilterBar({preset, onPresetChange}) {
  const selected = DATE_PRESETS.find((p) => p.id === preset);

  return (
    <div className="FilterBar">
      <div className="FilterBar__dateRange">
        <span className="FilterBar__label">{t('agenticControlPlane.dateRange')}</span>
        <MenuDropdown label={t(selected.translationKey)} size="sm">
          {DATE_PRESETS.map((p) => (
            <MenuItemSelectable
              key={p.id}
              label={t(p.translationKey)}
              selected={p.id === preset}
              onChange={() => onPresetChange(p.id)}
            />
          ))}
        </MenuDropdown>
      </div>
    </div>
  );
}
