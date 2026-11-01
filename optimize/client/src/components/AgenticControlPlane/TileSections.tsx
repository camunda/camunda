/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';

// @ts-expect-error no types yet
import {DashboardRenderer} from 'components/DashboardRenderer';
import {evaluateReport, type ReportEvaluationPayload} from 'services';

import type {DashboardTile} from 'types';

import {TileFooter} from './TileFooter';
import {getTileTopNLimit} from './tilePagination';

export type TileLevel = 'overview' | 'process' | 'agent';
export type TileSection = 'kpi' | 'trends' | 'agentTrends';

interface AgenticTileConfiguration {
  levels?: TileLevel[];
  section?: TileSection;
}

export interface Definition {
  key: string;
  versions: string[];
}

interface TileSectionsProps {
  tiles: DashboardTile[];
  level: TileLevel;
  sections: TileSection[];
  definitions: Definition[];
  filter: unknown[];
  groupByDateUnit: string;
  // Remounts the renderers when the scope changes so stale tile results are not shown.
  scopeKey: string;
}

type EvaluateFilter = Parameters<typeof evaluateReport>[1];
type EvaluateQuery = Parameters<typeof evaluateReport>[2];

export function tileLevels(tile: DashboardTile): TileLevel[] {
  return (tile.configuration as AgenticTileConfiguration | undefined)?.levels ?? ['overview'];
}

export function tileSection(tile: DashboardTile): TileSection {
  return (tile.configuration as AgenticTileConfiguration | undefined)?.section ?? 'kpi';
}

// KPI tiles are plain numbers; the trend tiles group by date, with a bucket size that follows the
// selected period.
const DATE_AWARE_SECTIONS: TileSection[] = ['trends', 'agentTrends'];

export function TileSections({
  tiles,
  level,
  sections,
  definitions,
  filter,
  groupByDateUnit,
  scopeKey,
}: TileSectionsProps) {
  const definitionsKey = JSON.stringify(definitions);

  const tileLimitById = useMemo(() => {
    const map: Record<string, number> = {};
    tiles.forEach((tile) => {
      const limit = getTileTopNLimit(tile);
      if (limit != null && tile.id) {
        map[tile.id] = limit;
      }
    });
    return map;
  }, [tiles]);

  const loadTile = useCallback(
    (id: ReportEvaluationPayload, tileFilter: EvaluateFilter, query: EvaluateQuery) =>
      evaluateReport(id, tileFilter, query, definitions),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [definitionsKey]
  );

  const loadDateAwareTile = useCallback(
    (id: ReportEvaluationPayload, tileFilter: EvaluateFilter, query: EvaluateQuery) => {
      // Tiles with a top N only render that many groups; the backend computes that subset
      // server-side when a limit is supplied, so we never fetch every group.
      const limit = typeof id === 'string' ? tileLimitById[id] : undefined;
      const scopedQuery = limit ? {...query, limit} : query;
      return evaluateReport(id, tileFilter, scopedQuery, definitions, groupByDateUnit);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [definitionsKey, groupByDateUnit, tileLimitById]
  );

  const levelTiles = tiles.filter((tile) => tileLevels(tile).includes(level));

  return (
    <>
      {sections.map((section) => (
        <div key={section} className={`${section}-section`}>
          <DashboardRenderer
            key={[scopeKey, level, section].join('-')}
            loadTile={DATE_AWARE_SECTIONS.includes(section) ? loadDateAwareTile : loadTile}
            tiles={levelTiles.filter((tile) => tileSection(tile) === section)}
            filter={filter}
            addons={[<TileFooter key="tile-footer" />]}
          />
        </div>
      ))}
    </>
  );
}
