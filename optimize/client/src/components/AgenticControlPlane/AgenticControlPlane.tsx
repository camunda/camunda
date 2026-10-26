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

import {DrillBreadcrumb} from './DrillBreadcrumb';
import {ALL_VERSIONS, DATE_PRESETS, FilterBar} from './FilterBar';
import {loadAgenticDashboard} from './service';
import {TileFooter} from './TileFooter';
import {getTileTopNLimit} from './tilePagination';

import './AgenticControlPlane.scss';

type DrillLevel = 'overview' | 'process' | 'agent';

interface AgenticTileConfiguration {
  levels?: DrillLevel[];
  section?: string;
  sectionByLevel?: Partial<Record<DrillLevel, string>>;
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

interface AgentRunFilter {
  type: 'agentRun';
  filterLevel: 'instance';
  data: {agentIds: string[]; models: string[]; providers: string[]};
}

function presetToGroupByDateUnit(presetId: string): string {
  if (presetId === '7d') {
    return 'day';
  }
  if (presetId === '30d') {
    return 'week';
  }
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
  const [versions, setVersions] = useState<string[]>([ALL_VERSIONS]);
  const [agentIds, setAgentIds] = useState<string[]>([]);
  const [models, setModels] = useState<string[]>([]);
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    mightFail(loadAgenticDashboard(), setDashboard, showError);
  }, [mightFail]);

  const handleProcessScopeChange = useCallback((key: string | null) => {
    setProcessScope(key);
    setVersions([ALL_VERSIONS]);
    // Agents and models are listed per process, so a selection from another process no longer
    // applies.
    setAgentIds([]);
    setModels([]);
  }, []);

  const selectedPreset = DATE_PRESETS.find((p) => p.id === preset)!;
  const definitions = processScope ? [{key: processScope, versions}] : [];
  const filter: (RollingFilter | AgentRunFilter)[] = [presetToFilter(selectedPreset)];
  if (agentIds.length > 0 || models.length > 0) {
    filter.push({
      type: 'agentRun',
      filterLevel: 'instance',
      data: {agentIds, models, providers: []},
    });
  }

  const scopedEvaluateReport = useCallback(
    (
      id: ReportEvaluationPayload,
      tileFilter: Parameters<typeof evaluateReport>[1],
      query: Parameters<typeof evaluateReport>[2]
    ) => evaluateReport(id, tileFilter, query, definitions),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [processScope, versions]
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

  const scopedEvaluateDateAwareReport = useCallback(
    (
      id: ReportEvaluationPayload,
      tileFilter: Parameters<typeof evaluateReport>[1],
      query: Parameters<typeof evaluateReport>[2]
    ) => {
      // Tiles with a top N only render that many groups; the backend computes that subset
      // server-side when a limit is supplied, so we never fetch every group.
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
    [processScope, versions, preset, tileLimitById]
  );

  if (!dashboard) {
    return <Loading />;
  }

  // Overview across all processes, then one process, then the selected agents of that process.
  const level: DrillLevel = !processScope ? 'overview' : agentIds.length > 0 ? 'agent' : 'process';

  const visibleTiles = dashboard.tiles?.filter((tile) =>
    ((tile.configuration as AgenticTileConfiguration | undefined)?.levels ?? ['overview']).includes(
      level
    )
  );

  const processSections = [
    {key: 'kpi', titleKey: undefined, loadTile: scopedEvaluateReport},
    {
      key: 'token',
      titleKey: 'agenticControlPlane.tokenUsage',
      loadTile: scopedEvaluateDateAwareReport,
    },
    {
      key: 'reliabilityAndToolCalls',
      titleKey: 'agenticControlPlane.reliabilityAndToolCalls',
      loadTile: scopedEvaluateReport,
    },
    {key: 'duration', titleKey: 'agenticControlPlane.duration', loadTile: scopedEvaluateReport},
  ];

  // Volume and cost tiles include weekly lines whose bucket size follows the date range.
  const agentSections = [
    {
      key: 'agentHealth',
      titleKey: 'agenticControlPlane.agentHealth',
      loadTile: scopedEvaluateReport,
    },
    // Process outcomes across all processes, only on the overview.
    ...(level === 'overview'
      ? [
          {
            key: 'processes',
            titleKey: 'agenticControlPlane.processes',
            loadTile: scopedEvaluateDateAwareReport,
          },
        ]
      : []),
    {
      key: 'volume',
      titleKey: 'agenticControlPlane.volume',
      loadTile: scopedEvaluateDateAwareReport,
    },
    {
      key: 'costAndUsage',
      titleKey: 'agenticControlPlane.costAndUsage',
      loadTile: scopedEvaluateDateAwareReport,
    },
  ];

  const sections = level === 'process' ? processSections : agentSections;
  const defaultSection = sections[0]!.key;

  return (
    <div className="AgenticControlPlane">
      <div className="header">
        <h1 className="title">{t('agenticControlPlane.title')}</h1>
        <p className="description">{t('agenticControlPlane.description')}</p>
        <DrillBreadcrumb
          processScope={processScope}
          agentIds={agentIds}
          onOverview={() => handleProcessScopeChange(null)}
          onProcess={() => {
            setAgentIds([]);
            setModels([]);
          }}
        />
      </div>
      <FilterBar
        preset={preset}
        onPresetChange={setPreset}
        processScope={processScope}
        onProcessScopeChange={handleProcessScopeChange}
        versions={versions}
        onVersionsChange={setVersions}
        agentIds={agentIds}
        onAgentIdsChange={setAgentIds}
        models={models}
        onModelsChange={setModels}
        // Process-level tiles read the agent data nested in process instances, which has no
        // per-run model to filter on.
        showModelFilter={level !== 'process'}
      />
      {sections.map(({key, titleKey, loadTile}) => {
        const tiles = visibleTiles?.filter((tile) => {
          const config = tile.configuration as AgenticTileConfiguration | undefined;
          return (config?.sectionByLevel?.[level] ?? config?.section ?? defaultSection) === key;
        });
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
                key={[
                  processScope ?? '__all__',
                  versions.join(','),
                  agentIds.join(','),
                  models.join(','),
                  key,
                ].join('-')}
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
