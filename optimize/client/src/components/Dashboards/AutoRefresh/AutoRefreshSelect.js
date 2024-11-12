/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';
import {MenuItemSelectable} from '@carbon/react';
import {UpdateNow} from '@carbon/icons-react';
import {MenuButton} from '@camunda/camunda-optimize-composite-components';

import {t} from 'translation';

import AutoRefreshIcon from './AutoRefreshIcon';

export default function AutoRefreshSelect({refreshRateMs, onRefresh, onChange, size}) {
  const [autoRefreshHandle, setAutoRefreshHandle] = useState(() =>
    refreshRateMs && onRefresh ? setInterval(onRefresh, refreshRateMs) : null
  );

  function setAutorefresh(newRefreshRateMs) {
    clearInterval(autoRefreshHandle);
    if (newRefreshRateMs && onRefresh) {
      setAutoRefreshHandle(setInterval(onRefresh, newRefreshRateMs));
    }
    onChange(newRefreshRateMs);
  }

  useEffect(() => {
    return () => {
      clearInterval(autoRefreshHandle);
    };
  }, [autoRefreshHandle]);

  const menuItems = [
    {value: 'off', label: t('common.off')},
    {value: '1', label: `1 ${t('common.unit.minute.label')}`},
    {value: '5', label: `5 ${t('common.unit.minute.label-plural')}`},
    {value: '10', label: `10 ${t('common.unit.minute.label-plural')}`},
    {value: '15', label: `15 ${t('common.unit.minute.label-plural')}`},
    {value: '30', label: `30 ${t('common.unit.minute.label-plural')}`},
    {value: '60', label: `60 ${t('common.unit.minute.label-plural')}`},
  ];

  return (
    <MenuButton
      className="AutoRefreshSelect"
      kind="ghost"
      label={onRefresh ? <AutoRefreshIcon interval={refreshRateMs} /> : <UpdateNow />}
      menuLabel={t('dashboard.autoRefresh')}
      size={size}
      iconDescription={t('dashboard.autoRefresh')}
      hasIconOnly
      menuTarget={document.querySelector('.fullscreen')}
    >
      {menuItems.map(({value, label}) => {
        const currentValue = !refreshRateMs ? 'off' : (refreshRateMs / (60 * 1000)).toString();
        return (
          <MenuItemSelectable
            key={value}
            value={value}
            label={label}
            selected={value === currentValue}
            onChange={() => setAutorefresh(value === 'off' ? null : value * 60 * 1000)}
          />
        );
      })}
    </MenuButton>
  );
}
