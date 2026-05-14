/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useMemo} from 'react';
import {useQuery} from '@tanstack/react-query';
import {InlineLoading, Tile} from '@carbon/react';
import {
  endpoints,
  type GetProcessDefinitionStatisticsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {requestWithThrow} from 'modules/request';
import type {WidgetConfig} from '../types';
import {useResolveProcessKey} from './useResolveProcessKey';
import {
  WidgetTitle,
  WidgetSubtitle,
  EmptyState,
  FunnelContainer,
  FunnelRow,
  FunnelBarTrack,
  FunnelBarFill,
  FunnelBarLabel,
  FunnelBarCount,
  FunnelDropoff,
} from '../styled';

type Props = {
  config: WidgetConfig;
};

type ElementStat = {
  elementId: string;
  active: number;
  canceled: number;
  incidents: number;
  completed: number;
};

const FunnelWidget: React.FC<Props> = ({config}) => {
  const {
    title,
    subtitle,
    processDefinitionKey: configKey,
    funnelStages = [],
  } = config;

  // Shared resolver — dedupes /v2/process-definitions/search across multiple
  // BPMN/funnel widgets in the same bundle that point at the same process.
  const looksNumeric = !!configKey && /^\d+$/.test(configKey);
  const {resolvedKey, status: resolveStatus} = useResolveProcessKey(configKey);
  const processDefinitionKey = resolvedKey;

  const {data: statsResponse, status: statsStatus} = useQuery({
    queryKey: ['notebook-funnel-stats', processDefinitionKey],
    enabled: !!processDefinitionKey,
    queryFn: async () => {
      if (!processDefinitionKey) {
        throw new Error('No processDefinitionKey');
      }
      const {response, error} =
        await requestWithThrow<GetProcessDefinitionStatisticsResponseBody>({
          url: endpoints.getProcessDefinitionStatistics.getUrl({
            processDefinitionKey,
            statisticName: 'element-instances',
          }),
          method: endpoints.getProcessDefinitionStatistics.method,
        });
      if (error) {
        throw error;
      }
      return response;
    },
  });

  const stageData = useMemo(() => {
    if (!statsResponse?.items || funnelStages.length === 0) {
      return [];
    }

    const statsMap = new Map<string, ElementStat>();
    for (const s of statsResponse.items as ElementStat[]) {
      statsMap.set(s.elementId, s);
    }

    return funnelStages.map((stage) => {
      const stat = statsMap.get(stage.elementId);
      const total = stat
        ? stat.active + stat.completed + stat.canceled + stat.incidents
        : 0;
      return {label: stage.label, total};
    });
  }, [statsResponse, funnelStages]);

  // --- missing config ---
  if (!configKey || funnelStages.length === 0) {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState>No process or stages configured for funnel.</EmptyState>
      </Tile>
    );
  }

  // --- loading ---
  if (
    (!looksNumeric && resolveStatus === 'pending') ||
    statsStatus === 'pending'
  ) {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <InlineLoading
          description="Loading funnel…"
          data-testid="funnel-loading"
        />
      </Tile>
    );
  }

  // --- error ---
  if ((!looksNumeric && resolveStatus === 'error') || statsStatus === 'error') {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState data-testid="funnel-error">
          Could not load funnel data.
        </EmptyState>
      </Tile>
    );
  }

  if (stageData.length === 0) {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState data-testid="funnel-empty">No stage data.</EmptyState>
      </Tile>
    );
  }

  const maxTotal = Math.max(...stageData.map((s) => s.total), 1);

  return (
    <Tile>
      <WidgetTitle>{title}</WidgetTitle>
      {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
      <FunnelContainer data-testid="funnel-chart">
        {stageData.map((stage, i) => {
          const pct = (stage.total / maxTotal) * 100;
          const prev = stageData[i - 1];
          const dropoff =
            prev && prev.total > 0
              ? Math.round(((prev.total - stage.total) / prev.total) * 100)
              : null;

          return (
            <FunnelRow key={stage.label}>
              <FunnelBarTrack>
                <FunnelBarFill $pct={pct} $colorIdx={i} />
                <FunnelBarLabel>{stage.label}</FunnelBarLabel>
                <FunnelBarCount>{stage.total.toLocaleString()}</FunnelBarCount>
              </FunnelBarTrack>
              {dropoff !== null && dropoff > 0 && (
                <FunnelDropoff>↓ {dropoff}% drop-off</FunnelDropoff>
              )}
            </FunnelRow>
          );
        })}
      </FunnelContainer>
    </Tile>
  );
};

export {FunnelWidget};
