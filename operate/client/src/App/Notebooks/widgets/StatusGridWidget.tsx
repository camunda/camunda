/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {useQuery} from '@tanstack/react-query';
import {Tile} from '@carbon/react';
import {requestWithThrow} from 'modules/request';
import type {WidgetConfig} from '../types';
import {
  WidgetTitle,
  WidgetSubtitle,
  EmptyState,
  StatusGridContainer,
  StatusTile,
  StatusTileName,
  StatusTileVersion,
  StatusTileCount,
  StatusTileSkeleton,
} from '../styled';

type Props = {
  config: WidgetConfig;
};

type ProcessDef = {
  processDefinitionKey: string;
  processDefinitionId: string;
  name?: string;
  version?: number;
};

type Incident = {
  processDefinitionKey?: string;
  processDefinitionId?: string;
};

function incidentStatus(count: number): 'healthy' | 'warning' | 'critical' {
  if (count === 0) {
    return 'healthy';
  }
  if (count <= 5) {
    return 'warning';
  }
  return 'critical';
}

const StatusGridWidget: React.FC<Props> = ({config}) => {
  const {title, subtitle} = config;

  const {data: defsData, status: defsStatus} = useQuery({
    queryKey: ['notebook-status-grid-defs', config.id],
    queryFn: async () => {
      const {response, error} = await requestWithThrow<{
        items: ProcessDef[];
      }>({
        url: '/v2/process-definitions/search',
        method: 'POST',
        body: {page: {limit: 100}},
      });
      if (error) {
        throw error;
      }
      return response;
    },
  });

  const {data: incidentsData} = useQuery({
    queryKey: ['notebook-status-grid-incidents', config.id],
    queryFn: async () => {
      const {response, error} = await requestWithThrow<{
        items: Incident[];
      }>({
        url: '/v2/incidents/search',
        method: 'POST',
        body: {filter: {state: 'ACTIVE'}, page: {limit: 1000}},
      });
      if (error) {
        throw error;
      }
      return response;
    },
  });

  if (defsStatus === 'pending') {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <StatusGridContainer data-testid="status-grid-loading">
          {Array.from({length: 6}).map((_, i) => (
            <StatusTileSkeleton key={i} />
          ))}
        </StatusGridContainer>
      </Tile>
    );
  }

  if (defsStatus === 'error') {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState data-testid="status-grid-error">
          Could not load process health.
        </EmptyState>
      </Tile>
    );
  }

  const defs = defsData?.items ?? [];

  if (defs.length === 0) {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState data-testid="status-grid-empty">
          No deployed processes.
        </EmptyState>
      </Tile>
    );
  }

  // Build incident count map keyed by processDefinitionKey
  const incidentsByKey = new Map<string, number>();
  for (const inc of incidentsData?.items ?? []) {
    const key = inc.processDefinitionKey ?? inc.processDefinitionId ?? '';
    if (key) {
      incidentsByKey.set(key, (incidentsByKey.get(key) ?? 0) + 1);
    }
  }

  return (
    <Tile>
      <WidgetTitle>{title}</WidgetTitle>
      {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
      <StatusGridContainer data-testid="status-grid">
        {defs.map((def) => {
          const count = incidentsByKey.get(def.processDefinitionKey) ?? 0;
          const status = incidentStatus(count);
          const displayName =
            def.name ?? def.processDefinitionId ?? def.processDefinitionKey;

          return (
            <StatusTile
              key={def.processDefinitionKey}
              $status={status}
              data-testid="status-tile"
            >
              <StatusTileName title={displayName}>{displayName}</StatusTileName>
              <StatusTileVersion>v{def.version ?? 1}</StatusTileVersion>
              <StatusTileCount $status={status}>
                {count} incident{count !== 1 ? 's' : ''}
              </StatusTileCount>
            </StatusTile>
          );
        })}
      </StatusGridContainer>
    </Tile>
  );
};

export {StatusGridWidget};
