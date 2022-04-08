/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';

import {Select, Icon} from 'components';
import {t} from 'translation';

import AutoRefreshIcon from './AutoRefreshIcon';

export default function AutoRefreshSelect({refreshRateMs, onRefresh, onChange}) {
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

  return (
    <Select
      main
      label={
        <>
          {onRefresh ? <AutoRefreshIcon interval={refreshRateMs} /> : <Icon type="autorefresh" />}
          {t('dashboard.autoRefresh')}
        </>
      }
      onChange={(value) => setAutorefresh(value === 'off' ? null : value * 60 * 1000)}
      value={!refreshRateMs ? 'off' : (refreshRateMs / (60 * 1000)).toString()}
    >
      <Select.Option value="off">{t('common.off')}</Select.Option>
      <Select.Option value="1">1 {t('common.unit.minute.label')}</Select.Option>
      <Select.Option value="5">5 {t('common.unit.minute.label-plural')}</Select.Option>
      <Select.Option value="10">10 {t('common.unit.minute.label-plural')}</Select.Option>
      <Select.Option value="15">15 {t('common.unit.minute.label-plural')}</Select.Option>
      <Select.Option value="30">30 {t('common.unit.minute.label-plural')}</Select.Option>
      <Select.Option value="60">60 {t('common.unit.minute.label-plural')}</Select.Option>
    </Select>
  );
}
