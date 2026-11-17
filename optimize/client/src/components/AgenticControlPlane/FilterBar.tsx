/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';
import {ComboBox, MenuItemSelectable} from '@carbon/react';

import {MenuDropdown} from 'components';
import {loadDefinitions, loadVersions} from 'services';
import type {Definition, Version} from 'services';
import {t} from 'translation';

import './FilterBar.scss';

export const DATE_PRESETS = [
  {id: '7d', translationKey: 'agenticControlPlane.presets.last7Days', value: 7, unit: 'days'},
  {id: '30d', translationKey: 'agenticControlPlane.presets.last30Days', value: 30, unit: 'days'},
  {id: '3m', translationKey: 'agenticControlPlane.presets.last3Months', value: 3, unit: 'months'},
  {id: '6m', translationKey: 'agenticControlPlane.presets.last6Months', value: 6, unit: 'months'},
  {
    id: '12m',
    translationKey: 'agenticControlPlane.presets.last12Months',
    value: 12,
    unit: 'months',
  },
] as const;

export const ALL_VERSIONS = 'all';
export const LATEST_VERSION = 'latest';

interface FilterBarProps {
  preset: string;
  onPresetChange: (id: string) => void;
  processScope: string | null;
  onProcessScopeChange: (key: string | null) => void;
  versions: string[];
  onVersionsChange: (versions: string[]) => void;
}

function formatSpecificVersion(version: string, versionTag: string | null | undefined): string {
  return (
    (t('agenticControlPlane.versionFilter.version', {version}) as string) +
    (versionTag ? ` (${versionTag})` : '')
  );
}

function versionLabel(versions: string[], available: Version[]): string {
  const selected = versions[0];
  if (selected === ALL_VERSIONS || selected == null) {
    return t('agenticControlPlane.versionFilter.all') as string;
  }
  if (selected === LATEST_VERSION) {
    return t('agenticControlPlane.versionFilter.latest') as string;
  }
  const match = available.find((v) => v.version === selected);
  return formatSpecificVersion(selected, match?.versionTag);
}

export function FilterBar({
  preset,
  onPresetChange,
  processScope,
  onProcessScopeChange,
  versions,
  onVersionsChange,
}: FilterBarProps) {
  const selected = DATE_PRESETS.find((p) => p.id === preset)!;
  const [processDefinitions, setProcessDefinitions] = useState<Definition[]>([]);
  const [availableVersions, setAvailableVersions] = useState<Version[]>([]);

  useEffect(() => {
    loadDefinitions('process', null).then(setProcessDefinitions);
  }, []);

  useEffect(() => {
    if (processScope) {
      loadVersions('process', null, processScope).then(setAvailableVersions);
    } else {
      setAvailableVersions([]);
    }
  }, [processScope]);

  const selectedProcess = processDefinitions.find((d) => d.key === processScope) ?? null;
  const selectedVersion = versions[0] ?? ALL_VERSIONS;

  return (
    <div className="FilterBar">
      <div className="processScope">
        <span className="label">{t('agenticControlPlane.processFilter.label')}</span>
        <ComboBox
          id="process-scope-filter"
          size="sm"
          items={processDefinitions}
          itemToString={(item) => item?.name ?? item?.key ?? ''}
          selectedItem={selectedProcess}
          onChange={({selectedItem}) => onProcessScopeChange?.(selectedItem?.key ?? null)}
          placeholder={t('agenticControlPlane.processFilter.placeholder') as string}
          titleText=""
        />
      </div>
      <div className="versionScope">
        <span className="label">{t('agenticControlPlane.versionFilter.label')}</span>
        <MenuDropdown
          label={versionLabel(versions, availableVersions)}
          size="sm"
          disabled={!processScope}
        >
          <MenuItemSelectable
            label={t('agenticControlPlane.versionFilter.all') as string}
            selected={selectedVersion === ALL_VERSIONS}
            onChange={() => onVersionsChange([ALL_VERSIONS])}
          />
          <MenuItemSelectable
            label={t('agenticControlPlane.versionFilter.latest') as string}
            selected={selectedVersion === LATEST_VERSION}
            onChange={() => onVersionsChange([LATEST_VERSION])}
          />
          {availableVersions.map((v) => (
            <MenuItemSelectable
              key={v.version}
              label={formatSpecificVersion(v.version, v.versionTag)}
              selected={selectedVersion === v.version}
              onChange={() => onVersionsChange([v.version])}
            />
          ))}
        </MenuDropdown>
      </div>
      <div className="dateRange">
        <span className="label">{t('agenticControlPlane.dateRange')}</span>
        <MenuDropdown label={t(selected.translationKey) as string} size="sm">
          {DATE_PRESETS.map((p) => (
            <MenuItemSelectable
              key={p.id}
              label={t(p.translationKey) as string}
              selected={p.id === preset}
              onChange={() => onPresetChange(p.id)}
            />
          ))}
        </MenuDropdown>
      </div>
    </div>
  );
}
