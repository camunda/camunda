/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect, useCallback} from 'react';

import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';
import {DashboardRenderer, Loading} from 'components';
import {evaluateReport} from 'services';
import {t} from 'translation';

import {DATE_PRESETS, FilterBar} from './FilterBar';
import {loadAgenticDashboard} from './service';

import './AgenticControlPlane.scss';

interface RollingFilter {
  type: 'instanceEndDate';
  filterLevel: 'instance';
  data: {
    type: 'rolling';
    start: {value: number; unit: string};
    end: null;
    excludeUndefined: boolean;
    includeUndefined: boolean;
  };
}

function presetToFilter(preset: (typeof DATE_PRESETS)[number]): RollingFilter {
  return {
    type: 'instanceEndDate',
    filterLevel: 'instance',
    data: {
      type: 'rolling',
      start: {value: preset.value, unit: preset.unit},
      end: null,
      excludeUndefined: false,
      includeUndefined: false,
    },
  };
}

export function AgenticControlPlane() {
  const [preset, setPreset] = useState('30d');
  const [dashboard, setDashboard] = useState<{tiles: unknown[]} | null>(null);
  const [processScope, setProcessScope] = useState<string | null>(null);
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    mightFail(loadAgenticDashboard(), setDashboard, showError);
  }, [mightFail]);

  const selectedPreset = DATE_PRESETS.find((p) => p.id === preset)!;
  const definitions = processScope ? [{key: processScope, versions: ['all']}] : [];
  const filter = [presetToFilter(selectedPreset)];

  const scopedEvaluateReport = useCallback(
    (id: string, tileFilter: unknown, query: unknown) =>
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      evaluateReport(id as any, tileFilter as any, query as any, definitions),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [processScope]
  );

  if (!dashboard) {
    return <Loading />;
  }

  const visibleTiles = (dashboard.tiles as any[])?.filter((tile) => {
    if (processScope && tile.configuration?.visibleInL0Only) {
      return false;
    }
    if (!processScope && tile.configuration?.visibleInL1Only) {
      return false;
    }
    return true;
  });

  const sections = [
    {key: 'kpi', loadTile: scopedEvaluateReport},
    {key: 'token', titleKey: 'agenticControlPlane.tokenUsage', loadTile: scopedEvaluateReport},
  ];

  return (
    <div className="AgenticControlPlane">
      <div className="header">
        <h1 className="title">{t('agenticControlPlane.title')}</h1>
        <p className="description">{t('agenticControlPlane.description')}</p>
      </div>
      <FilterBar
        preset={preset}
        onPresetChange={setPreset}
        processScope={processScope}
        onProcessScopeChange={setProcessScope}
      />
      {sections.map(({key, titleKey, loadTile}) => {
        const tiles = visibleTiles?.filter(
          (tile) => (tile.configuration?.section ?? 'kpi') === key
        );
        return (
          <div key={key}>
            {titleKey && (
              <>
                <h3 className="section-title">{t(titleKey)}</h3>
                <hr className="section-divider" />
              </>
            )}
            <div className={`${key}-section`}>
              <DashboardRenderer
                key={`${processScope ?? '__all__'}-${key}`}
                loadTile={loadTile}
                tiles={tiles}
                filter={filter}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
