/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useMemo} from 'react';
import {observer} from 'mobx-react';
import {useQuery} from '@tanstack/react-query';
import {InlineLoading, Tile} from '@carbon/react';
import {createPortal} from 'react-dom';
import {
  endpoints,
  type GetProcessDefinitionStatisticsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {requestWithThrow} from 'modules/request';
import {Diagram} from 'modules/components/Diagram';
import {useResolveProcessKey} from './useResolveProcessKey';
import {ACTIVE_BADGE, INCIDENTS_BADGE} from 'modules/bpmn-js/badgePositions';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import type {OverlayData} from 'modules/bpmn-js/BpmnJS';
import type {WidgetConfig} from '../types';
import {
  WidgetTitle,
  WidgetSubtitle,
  EmptyState,
  BpmnDiagramContainer,
  OverlayBadge,
  BpmnHeatmapStyles,
  BpmnLegend,
  BpmnLegendItem,
  BpmnLegendDot,
  BpmnLegendGradient,
} from '../styled';

type Props = {
  config: WidgetConfig;
};

// ---------------------------------------------------------------------------
// Element-instance statistics type (per flow-node)
// ---------------------------------------------------------------------------

type ElementStat = {
  elementId: string;
  active: number;
  canceled: number;
  incidents: number;
  completed: number;
};

// ---------------------------------------------------------------------------
// Overlay badge helpers
// ---------------------------------------------------------------------------

// IMPORTANT: diagramOverlaysStore is a global singleton — when multiple BPMN
// widgets are mounted at once (e.g. "Compare all processes" with 3 diagrams),
// each widget reads the full overlay array and would portal badges into every
// container. Result without scoping: each badge appears N times on the
// last-mounted diagram.
//
// Fix: scope the overlay type by widget id. Each widget's overlays then have
// unique types in the store, and each widget filters by its own pair.
const OVERLAY_TYPE_ACTIVE_PREFIX = 'notebook-bpmn-active::';
const OVERLAY_TYPE_INCIDENTS_PREFIX = 'notebook-bpmn-incidents::';
function activeTypeFor(widgetId: string) {
  return OVERLAY_TYPE_ACTIVE_PREFIX + widgetId;
}
function incidentsTypeFor(widgetId: string) {
  return OVERLAY_TYPE_INCIDENTS_PREFIX + widgetId;
}

/**
 * Renders a badge into the DOM container that bpmn-js created for this overlay.
 * We use createPortal so the badge is a real React element but mounted inside
 * the bpmn-js canvas at the correct flow-node position.
 */
const ActiveBadge: React.FC<{container: HTMLElement; count: number}> = ({
  container,
  count,
}) =>
  createPortal(
    <OverlayBadge $color="var(--cds-support-info)" data-testid="overlay-active">
      {count}
    </OverlayBadge>,
    container,
  );

const IncidentBadge: React.FC<{container: HTMLElement; count: number}> = ({
  container,
  count,
}) =>
  createPortal(
    <OverlayBadge
      $color="var(--cds-support-error)"
      data-testid="overlay-incident"
    >
      {count}
    </OverlayBadge>,
    container,
  );

// ---------------------------------------------------------------------------
// Build OverlayData descriptors from element stats
// ---------------------------------------------------------------------------

type OverlayMode =
  | 'active'
  | 'incidents'
  | 'combined'
  | 'stuck'
  | 'none'
  | 'heatmap';

function buildOverlayData(
  stats: ElementStat[],
  mode: OverlayMode,
  widgetId: string,
): OverlayData[] {
  if (mode === 'none') {
    return [];
  }
  const TYPE_ACTIVE = activeTypeFor(widgetId);
  const TYPE_INCIDENTS = incidentsTypeFor(widgetId);

  const overlays: OverlayData[] = [];

  // For heatmap mode, only show incident badges on the hottest nodes (bucket 3+)
  // to confirm the intensity is data-driven, not purely decorative.
  if (mode === 'heatmap') {
    const maxIncidents = Math.max(...stats.map((s) => s.incidents), 1);
    for (const stat of stats) {
      if (stat.incidents > 0) {
        const intensity = stat.incidents / maxIncidents;
        const bucket = Math.min(4, Math.floor(intensity * 5));
        if (bucket >= 3) {
          overlays.push({
            elementId: stat.elementId,
            type: TYPE_INCIDENTS,
            position: INCIDENTS_BADGE,
            payload: {count: stat.incidents},
          });
        }
      }
    }
    return overlays;
  }

  for (const stat of stats) {
    if (mode === 'active' || mode === 'stuck') {
      // TODO: stuck overlay is currently equivalent to active.
      // Productionize with age math from element-instances once that data
      // is available from the API (e.g. oldest token creation date per element).
      if (stat.active > 0) {
        overlays.push({
          elementId: stat.elementId,
          type: TYPE_ACTIVE,
          position: ACTIVE_BADGE,
          payload: {count: stat.active},
        });
      }
    } else if (mode === 'incidents') {
      if (stat.incidents > 0) {
        overlays.push({
          elementId: stat.elementId,
          type: TYPE_INCIDENTS,
          position: INCIDENTS_BADGE,
          payload: {count: stat.incidents},
        });
      }
    } else if (mode === 'combined') {
      if (stat.active > 0) {
        overlays.push({
          elementId: stat.elementId,
          type: TYPE_ACTIVE,
          position: ACTIVE_BADGE,
          payload: {count: stat.active},
        });
      }
      if (stat.incidents > 0) {
        overlays.push({
          elementId: stat.elementId,
          type: TYPE_INCIDENTS,
          position: INCIDENTS_BADGE,
          payload: {count: stat.incidents},
        });
      }
    }
  }

  return overlays;
}

/**
 * For heatmap mode: compute [elementId, className] tuples where className is
 * notebook-heatmap-{0..4}. Elements with 0 incidents are excluded.
 */
function buildHeatmapClasses(
  stats: ElementStat[],
): [elementId: string, className: string][] {
  const maxIncidents = Math.max(...stats.map((s) => s.incidents), 1);
  const result: [string, string][] = [];

  for (const stat of stats) {
    if (stat.incidents > 0) {
      const intensity = stat.incidents / maxIncidents;
      const bucket = Math.min(4, Math.floor(intensity * 5));
      result.push([stat.elementId, `notebook-heatmap-${bucket}`]);
    }
  }

  return result;
}

// ---------------------------------------------------------------------------
// BpmnWidget
// ---------------------------------------------------------------------------

// Wrapped in mobx-react `observer` so the component re-renders when the
// diagramOverlaysStore (which holds bpmn-js overlay container refs) changes.
// Without this, the first render captures an empty store and badges never
// portal into the diagram even though the data is loaded.
const BpmnWidget: React.FC<Props> = observer(({config}) => {
  const {
    title,
    subtitle,
    processDefinitionKey: configKey,
    overlay = 'combined',
  } = config;

  // The V2 XML/statistics endpoints require the *numeric* processDefinitionKey,
  // but the LLM (and preset templates) often pass the human-readable
  // processDefinitionId (e.g. "order-process"). The shared hook resolves the
  // string to the numeric key, deduping requests across multiple BPMN/funnel
  // widgets in the same bundle that point at the same process.
  const looksNumeric = !!configKey && /^\d+$/.test(configKey);
  const {resolvedKey, status: resolveStatus} = useResolveProcessKey(configKey);
  const processDefinitionKey = resolvedKey;

  // Fetch BPMN XML
  const {data: xml, status: xmlStatus} = useQuery({
    queryKey: ['notebook-bpmn-xml', processDefinitionKey],
    enabled: !!processDefinitionKey,
    queryFn: async () => {
      if (!processDefinitionKey) {
        throw new Error('No processDefinitionKey');
      }
      const {response, error} = await requestWithThrow<string>({
        url: endpoints.getProcessDefinitionXml.getUrl({processDefinitionKey}),
        method: endpoints.getProcessDefinitionXml.method,
        responseType: 'text',
      });
      if (error) {
        throw error;
      }
      return response;
    },
  });

  // Fetch element-instance statistics (only when overlay is requested)
  const {data: statsResponse} = useQuery({
    queryKey: ['notebook-bpmn-stats', processDefinitionKey, overlay],
    enabled: !!processDefinitionKey && overlay !== 'none',
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

  // Per-instance overlay type strings — keeps this widget's badges separate
  // from any sibling BPMN widgets in the notebook (see comment near
  // OVERLAY_TYPE_ACTIVE_PREFIX above).
  const widgetId = config.id;
  const myActiveType = activeTypeFor(widgetId);
  const myIncidentsType = incidentsTypeFor(widgetId);

  const overlayData = useMemo(() => {
    if (!statsResponse?.items) {
      return [];
    }
    return buildOverlayData(
      statsResponse.items as ElementStat[],
      overlay as OverlayMode,
      widgetId,
    );
  }, [statsResponse, overlay, widgetId]);

  // Heatmap class tuples — used when overlay === 'heatmap'
  const heatmapClasses = useMemo(() => {
    if (overlay !== 'heatmap' || !statsResponse?.items) {
      return undefined;
    }
    return buildHeatmapClasses(statsResponse.items as ElementStat[]);
  }, [statsResponse, overlay]);

  // Highlighted element ids — elements with incidents get a visual emphasis
  const highlightedElementIds = useMemo(() => {
    if (
      overlay !== 'incidents' &&
      overlay !== 'combined' &&
      overlay !== 'heatmap'
    ) {
      return undefined;
    }
    return statsResponse?.items
      ?.filter((s) => (s as ElementStat).incidents > 0)
      .map((s) => (s as ElementStat).elementId);
  }, [statsResponse, overlay]);

  // Retrieve overlay containers from the MobX store — filter by THIS widget's
  // unique type strings so we don't pick up siblings' overlays.
  const storeOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === myActiveType || type === myIncidentsType,
  );

  // --- missing processDefinitionKey ---
  if (!configKey) {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState>No process definition key configured.</EmptyState>
      </Tile>
    );
  }

  // --- resolve failed (id → numeric key lookup) ---
  if (!looksNumeric && resolveStatus === 'error') {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState data-testid="bpmn-error">
          Process &quot;{configKey}&quot; not found.
        </EmptyState>
      </Tile>
    );
  }

  // --- loading (either resolving the id or fetching the xml) ---
  if (
    (!looksNumeric && resolveStatus === 'pending') ||
    xmlStatus === 'pending'
  ) {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <InlineLoading
          description="Loading diagram…"
          data-testid="bpmn-loading"
        />
      </Tile>
    );
  }

  // --- error ---
  if (xmlStatus === 'error' || !xml) {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState data-testid="bpmn-error">
          Could not load diagram.
        </EmptyState>
      </Tile>
    );
  }

  // Legend content varies by overlay mode — explains exactly what the audience
  // is seeing (badges, colors, intensity).
  const renderLegend = () => {
    if (overlay === 'none') {
      return null;
    }
    if (overlay === 'heatmap') {
      return (
        <BpmnLegend>
          <BpmnLegendItem>
            <BpmnLegendGradient />
            Incident intensity (pale → deep red)
          </BpmnLegendItem>
          <BpmnLegendItem>
            <BpmnLegendDot $color="var(--cds-support-error)" />
            Badge on hottest tasks shows incident count
          </BpmnLegendItem>
        </BpmnLegend>
      );
    }
    return (
      <BpmnLegend>
        {(overlay === 'active' ||
          overlay === 'combined' ||
          overlay === 'stuck') && (
          <BpmnLegendItem>
            <BpmnLegendDot $color="var(--cds-support-info)" />
            Active instance count
          </BpmnLegendItem>
        )}
        {(overlay === 'incidents' || overlay === 'combined') && (
          <BpmnLegendItem>
            <BpmnLegendDot $color="var(--cds-support-error)" />
            Open incidents
          </BpmnLegendItem>
        )}
      </BpmnLegend>
    );
  };

  return (
    <Tile>
      <WidgetTitle>{title}</WidgetTitle>
      {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
      <BpmnDiagramContainer data-testid="bpmn-diagram-container">
        {overlay === 'heatmap' && <BpmnHeatmapStyles />}
        <Diagram
          xml={xml}
          overlaysData={overlayData}
          highlightedElementIds={highlightedElementIds}
          customElementClasses={heatmapClasses}
        >
          {storeOverlays.map((storeOverlay) => {
            const payload = storeOverlay.payload as {count: number};

            if (storeOverlay.type === myActiveType) {
              return (
                <ActiveBadge
                  key={`active-${storeOverlay.elementId}`}
                  container={storeOverlay.container}
                  count={payload.count}
                />
              );
            }

            if (storeOverlay.type === myIncidentsType) {
              return (
                <IncidentBadge
                  key={`incidents-${storeOverlay.elementId}`}
                  container={storeOverlay.container}
                  count={payload.count}
                />
              );
            }

            return null;
          })}
        </Diagram>
      </BpmnDiagramContainer>
      {renderLegend()}
    </Tile>
  );
});

export {BpmnWidget};
