/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect, useCallback, useMemo} from 'react';

import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';
import {Loading} from 'components';
// @ts-expect-error no types yet
import {DashboardRenderer} from 'components/DashboardRenderer';
import {evaluateReport, type ReportEvaluationPayload} from 'services';
import {t} from 'translation';

import type {DashboardTile} from 'types';

import {DATE_PRESETS, FilterBar} from './FilterBar';
import {loadAgenticDashboard} from './service';
import {TileFooter} from './TileFooter';
import {getTileTopNLimit} from './tilePagination';

import './AgenticControlPlane.scss';

interface AgenticTileConfiguration {
  visibleInL0Only?: boolean;
  visibleInL1Only?: boolean;
  section?: string;
  footnote?: string;
  topN?: string;
}

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

function presetToGroupByDateUnit(presetId: string): string {
  if (presetId === '7d') return 'day';
  if (presetId === '30d') return 'week';
  return 'month';
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
  const [dashboard, setDashboard] = useState<{tiles: DashboardTile[]} | null>(null);
  const [processScope, setProcessScope] = useState<string | null>(null);
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    mightFail(loadAgenticDashboard(), setDashboard, showError);
  }, [mightFail]);

  const selectedPreset = DATE_PRESETS.find((p) => p.id === preset)!;
  const definitions = processScope ? [{key: processScope, versions: ['all']}] : [];
  const filter = [presetToFilter(selectedPreset)];

  const scopedEvaluateReport = useCallback(
    (
      id: ReportEvaluationPayload,
      tileFilter: Parameters<typeof evaluateReport>[1],
      query: Parameters<typeof evaluateReport>[2]
    ) => evaluateReport(id, tileFilter, query, definitions),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [processScope]
  );

  const tileLimitById = useMemo(() => {
    const map: Record<string, number> = {};
    dashboard?.tiles?.forEach((tile) => {
      const limit = getTileTopNLimit(tile);
      if (limit != null && tile.id) {
        map[tile.id] = limit;
      }
    });
    return map;
  }, [dashboard]);

  const scopedEvaluateTokenTrendReport = useCallback(
    (
      id: ReportEvaluationPayload,
      tileFilter: Parameters<typeof evaluateReport>[1],
      query: Parameters<typeof evaluateReport>[2]
    ) => {
      // The top consumers tile only renders the top N processes; the backend computes that
      // subset server-side when a limit is supplied, so we never fetch every process.
      const limit = typeof id === 'string' ? tileLimitById[id] : undefined;
      const scopedQuery = limit ? {...query, limit} : query;
      return evaluateReport(
        id,
        tileFilter,
        scopedQuery,
        definitions,
        presetToGroupByDateUnit(preset)
      );
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [processScope, preset, tileLimitById]
  );

  if (!dashboard) {
    return <Loading />;
  }

  const visibleTiles = dashboard.tiles?.filter((tile) => {
    const config = tile.configuration as AgenticTileConfiguration | undefined;
    if (processScope && config?.visibleInL0Only) {
      return false;
    }
    if (!processScope && config?.visibleInL1Only) {
      return false;
    }
    return true;
  });

  const sections = [
    {key: 'kpi', loadTile: scopedEvaluateReport},
    {
      key: 'token',
      titleKey: 'agenticControlPlane.tokenUsage',
      loadTile: scopedEvaluateTokenTrendReport,
    },
    {key: 'duration', titleKey: 'agenticControlPlane.duration', loadTile: scopedEvaluateReport},
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
          (tile) =>
            ((tile.configuration as AgenticTileConfiguration | undefined)?.section ?? 'kpi') === key
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
                addons={[<TileFooter key="tile-footer" />]}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
