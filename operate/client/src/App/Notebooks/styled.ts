/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {createGlobalStyle} from 'styled-components';
import {styles} from '@carbon/elements';

// ---------------------------------------------------------------------------
// Composable height tiers — every widget falls into one of three sizes that
// stack and pair cleanly given the WidgetsGrid 32px row gap:
//   - SHORT  132px  : metric, trend
//   - TALL   296px  : kpi, chart, funnel, activity-feed
//   - HERO   480px  : bpmn, status-grid
//
// Why these numbers? With the grid's 32px row gap:
//   2 × SHORT + 1 gap = 132 + 32 + 132 = 296 = TALL
//   3 × SHORT + 2 gap = 132 × 3 + 32 × 2 = 460 ≈ HERO
//
// Result: a column of N stacked SHORTs aligns with TALL/HERO neighbours
// in the next column — no awkward gaps.
//
// Auto widgets (table, text) take their natural height and live on their own
// 12-col row, so vertical alignment with neighbours doesn't apply.
// ---------------------------------------------------------------------------
const TILE_HEIGHT_SHORT = '132px';
const TILE_HEIGHT_TALL = '296px';
const TILE_HEIGHT_HERO = '480px';

/**
 * Two-column grid: scrollable content area (1fr) on the left and a sticky
 * 360px prompt rail on the right. The page itself fills the parent
 * (PageContent is height:100%), so the content area scrolls internally
 * while the prompt rail stays in view.
 */
const PageContainer = styled.div`
  display: grid;
  grid-template-columns: 1fr 520px;
  height: 100%;
  width: 100%;
  max-width: 1600px;
  margin: 0 auto;

  @media (max-width: 1100px) {
    /* Below 1100px there's no room for both columns. Collapse to a single
       column with the prompt back at the bottom (the way it was). */
    grid-template-columns: 1fr;
    grid-template-rows: 1fr auto;
  }
`;

const ContentScroll = styled.div`
  overflow-y: auto;
  padding: var(--cds-spacing-07) var(--cds-spacing-07) var(--cds-spacing-05);
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-06);
`;

/**
 * Top-of-content header row: notebook title on the left, action buttons
 * (e.g. Clear all) on the right.
 */
const NotebookHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cds-spacing-04);
`;

const NotebookTitle = styled.h1`
  ${styles.productiveHeading04};
  color: var(--cds-text-primary);
  margin: 0;
  flex: 1;
  min-width: 0;
  /* Allow long auto-derived titles ("give me a deep dive dashboard for the
     payment process") to wrap onto multiple lines instead of being clipped.
     The !important overrides Carbon's productiveHeading04 mixin which sets
     a no-wrap text style on h1 by default. */
  white-space: normal !important;
  overflow: visible;
  text-overflow: unset;
  overflow-wrap: anywhere;
  word-break: break-word;
  line-height: 1.25;
`;

const WidgetsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: var(--cds-spacing-05);
  flex: 1;
  align-content: start;
  /* Strict row order — DOM order = visual order. We do NOT use grid-auto-flow:
     dense because it lets later widgets backfill earlier gaps. When the user
     submits two prompts in a row, dense flow causes widgets from the second
     prompt's "short" row to fill leftover columns from the first prompt's
     last row, visually merging the two batches. Presets are sized so they
     compose without internal gaps — no need for dense. */
  grid-auto-flow: row;
`;

/**
 * Per-widget grid placement and TIER. Three height tiers compose cleanly with
 * the 32px row gap:
 *
 *  - SHORT (132px, 3 cols): metric, trend
 *  - TALL  (296px, 6 cols): kpi, chart, funnel, activity-feed
 *  - HERO  (~480px, 12 cols): bpmn, status-grid — full-width
 *  - AUTO  (variable, 12 cols): table, text — full-width, own row
 *
 * Stacking math: 2 × SHORT + gap = 132 + 32 + 132 = 296 = TALL.
 * So a column of 2 SHORTs aligns top + bottom with 1 TALL beside them.
 * 3 SHORTs stack to ~460px, close to a HERO.
 *
 * For TALL widgets we set min-height: TILE_HEIGHT_TALL on the inner Tile via
 * a CSS rule below — so all TALLs in the same row at least floor at 296px,
 * and don't wildly disagree on height.
 */
const WidgetSlot = styled.div<{
  $type: string;
  $chartType?: string;
  $activityFeedSize?: 'tall' | 'hero';
}>`
  grid-column: span
    ${({$type, $chartType, $activityFeedSize}) => {
      // Activity-feed hero variant spans full width.
      if ($type === 'activity-feed' && $activityFeedSize === 'hero') {
        return 12;
      }
      // Radar charts need a square-ish aspect ratio to render the polygon
      // legibly; at 6 cols they get squashed into a thin sliver. Span the
      // full row so the radar gets ~960px of width.
      if ($type === 'chart' && $chartType === 'radar') {
        return 12;
      }
      // SHORT (3 cols): metric tiles + trend (which is also a metric tile
      // shape with a tiny inline sparkline)
      if ($type === 'metric' || $type === 'trend') {
        return 3;
      }
      if (
        $type === 'table' ||
        $type === 'bpmn' ||
        $type === 'text' ||
        $type === 'status-grid'
      ) {
        return 12;
      }
      // KPI tiles always span the full width — they're a horizontal row of
      // numbers and need every column they can get so the labels never clip.
      if ($type === 'kpi') {
        return 12;
      }
      if (
        $type === 'chart' ||
        $type === 'activity-feed' ||
        $type === 'funnel'
      ) {
        return 6;
      }
      return 6;
    }};
  min-width: 0;

  @media (max-width: 900px) {
    grid-column: span ${({$type}) => ($type === 'metric' ? 6 : 12)};
  }
  @media (max-width: 600px) {
    grid-column: span 12;
  }

  /* Each slot keeps its widget's natural height — stretching short content
     (e.g. a sparkline) to match a tall neighbour leaves an awkward empty band.
     For TALL widgets we floor at TILE_HEIGHT_TALL on their inner Tile so
     adjacent TALLs in the same row at least share a minimum baseline. */
  align-self: start;

  /* Lock TALL-tier widgets to the TALL height. Both min and max are set so
     every TALL widget (chart, KPI, funnel, activity feed) lines
     up at 296px in the same row — no gaps, no overflow. The widget's
     internal scrollable region (list, feed) handles content overflow. */
  &[data-tier='tall'] > div > .cds--tile {
    min-height: ${TILE_HEIGHT_TALL};
    max-height: ${TILE_HEIGHT_TALL};
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  /* HERO activity-feed: full-width 480px tile with internal scroll.
     The ActivityFeed inside handles overflow via overflow-y: auto. */
  &[data-tier='hero'][data-type='activity-feed'] > div > .cds--tile {
    min-height: ${TILE_HEIGHT_HERO};
    max-height: ${TILE_HEIGHT_HERO};
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  /* HERO chart (currently used for radar): full-width 480px tile so the
     polygon can render with a near-square aspect ratio. Without this the
     chart inherits its component-level CHART_HEIGHT (240px) and squashes. */
  &[data-tier='hero'][data-type='chart'] > div > .cds--tile {
    min-height: ${TILE_HEIGHT_HERO};
    max-height: ${TILE_HEIGHT_HERO};
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  /* Cascade fade-in on mount. The animation-delay is set inline per index. */
  animation: notebook-widget-appear 320ms cubic-bezier(0.2, 0.8, 0.2, 1)
    backwards;

  @keyframes notebook-widget-appear {
    from {
      opacity: 0;
      transform: translateY(12px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }
`;

/**
 * Right-side prompt rail. Compact: textarea on top, button below it, then
 * pills wrap-flowing as content-sized chips. Below 1100px it becomes a
 * bottom strip (the layout we had before).
 */
const PromptSection = styled.aside`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-04);
  padding: var(--cds-spacing-05);
  background: var(--cds-layer);
  border-left: 1px solid var(--cds-border-subtle);
  /* Sit at the top of the rail, content-sized — don't stretch the section
     to the full grid row height. */
  align-self: start;

  @media (max-width: 1100px) {
    border-left: none;
    border-top: 1px solid var(--cds-border-subtle);
    background: var(--cds-background);
    padding: var(--cds-spacing-04) var(--cds-spacing-07) var(--cds-spacing-05);
  }
`;

/**
 * Section header for the rail. Tight — just a small uppercase label so the
 * eye finds the rail quickly without taking vertical real estate.
 */
const PromptSectionTitle = styled.h3`
  ${styles.label01};
  text-transform: uppercase;
  letter-spacing: 0.32px;
  color: var(--cds-text-secondary);
  margin: 0;

  @media (max-width: 1100px) {
    display: none;
  }
`;

const PromptSectionHint = styled.p`
  ${styles.helperText01};
  color: var(--cds-text-helper);
  margin: 0 0 var(--cds-spacing-02) 0;

  @media (max-width: 1100px) {
    display: none;
  }
`;

/**
 * Pill row — content-sized chips that wrap. Same layout in both the rail
 * and the bottom-strip; only the surrounding flow direction changes.
 * Pills hover with a subtle lift, no fixed width.
 */
const PromptSuggestions = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--cds-spacing-02);
  margin-top: var(--cds-spacing-02);

  .cds--tag {
    cursor: pointer;
    transition: transform 120ms ease;
    margin: 0;
  }
  .cds--tag:hover:not([disabled]) {
    transform: translateY(-1px);
  }

  @media (max-width: 1100px) {
    margin-top: 0;
    .cds--tag {
      width: auto;
    }
    .cds--tag:hover:not([disabled]) {
      transform: translateY(-1px);
    }
  }
`;

const PromptRow = styled.div`
  display: flex;
  gap: var(--cds-spacing-03);
  align-items: center;
  justify-content: flex-end;
  /* Button stays content-sized; if the InlineLoading is also rendered, the
     loading indicator pushes the button to the right. */
`;

const WidgetTitle = styled.h4`
  ${styles.productiveHeading02};
  color: var(--cds-text-primary);
  margin: 0 0 var(--cds-spacing-04) 0;
`;

/**
 * Optional helper line rendered directly below WidgetTitle.
 * Adds context such as sort order, active filters, or data currency.
 * Only render when config.subtitle is set.
 */
const WidgetSubtitle = styled.p`
  ${styles.helperText01};
  color: var(--cds-text-helper);
  margin: calc(var(--cds-spacing-03) * -1) 0 var(--cds-spacing-04) 0;
`;

/**
 * Centering wrapper for circular charts (pie/donut). Carbon Charts renders
 * its SVG anchored to the left of its container; without this wrapper the
 * disk sits flush-left while the bottom legend takes the full width.
 */
const CircularChartWrap = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;

  /* Carbon Charts root — let it consume full width so the inner SVG can
     center itself; the chart layout engine reads container size and lays
     out disk + legend within. */
  .cds--cc--chart-wrapper {
    width: 100%;
  }
`;

/**
 * Thin horizontal rule used to visually separate sections inside a tile.
 */
const WidgetDivider = styled.hr`
  border: none;
  border-top: 1px solid var(--cds-border-subtle);
  margin: var(--cds-spacing-05) 0 var(--cds-spacing-04) 0;
`;

// Map accent token to the corresponding Carbon CSS custom property.
const ACCENT_COLOR: Record<string, string> = {
  info: 'var(--cds-support-info)',
  success: 'var(--cds-support-success)',
  warning: 'var(--cds-support-warning)',
  error: 'var(--cds-support-error)',
  neutral: 'var(--cds-border-strong)',
};

/**
 * Polished metric tile: small uppercase caption + huge value.
 * The ::before adds a 4px accent stripe on the left edge.
 * Pass $accent to control the stripe color (defaults to 'info').
 */
const MetricTile = styled.div<{$accent?: string}>`
  position: relative;
  padding: var(--cds-spacing-05);
  background: var(--cds-layer);
  border-radius: 2px;
  /* SHORT tier — fixed so trend/metric/error/loading all measure exactly
     the same in any row state (no min-height drift). */
  height: ${TILE_HEIGHT_SHORT};
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--cds-spacing-03);
  overflow: hidden;
  transition:
    transform 160ms ease,
    box-shadow 160ms ease;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    bottom: 0;
    width: 4px;
    background: ${({$accent = 'info'}) =>
      ACCENT_COLOR[$accent] ?? ACCENT_COLOR['info']};
  }

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
  }
`;

const MetricCaption = styled.div`
  ${styles.label01};
  color: var(--cds-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.32px;
`;

const MetricValue = styled.div`
  ${styles.productiveHeading07};
  color: var(--cds-text-primary);
  font-variant-numeric: tabular-nums;
  line-height: 1;
`;

const MetricSubvalue = styled.div`
  ${styles.helperText01};
  color: var(--cds-text-helper);
`;

const WidgetTable = styled.table`
  width: 100%;
  border-collapse: collapse;
  ${styles.bodyShort01};

  th,
  td {
    text-align: left;
    padding: var(--cds-spacing-03) var(--cds-spacing-04);
    border-bottom: 1px solid var(--cds-border-subtle);
  }

  th {
    ${styles.productiveHeading01};
    color: var(--cds-text-secondary);
    background: var(--cds-layer-accent);
  }

  td {
    color: var(--cds-text-primary);
  }

  /* Linkable cell values (instance keys → Operate's instance page).
     Carbon's link blue with subtle hover underline. */
  td a {
    color: var(--cds-link-primary);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
`;

const EmptyState = styled.p`
  ${styles.bodyShort01};
  color: var(--cds-text-helper);
  margin: 0;
`;

/**
 * TextWidget — narrative/markdown cell. Designed to feel like editorial copy
 * floating above the data widgets, not a card. No background, no border.
 */
const TextWidgetContainer = styled.div`
  padding: var(--cds-spacing-04) 0;
  color: var(--cds-text-primary);

  h1,
  h2,
  h3,
  h4 {
    margin: 0 0 var(--cds-spacing-04) 0;
    color: var(--cds-text-primary);
  }
  h1 {
    ${styles.productiveHeading05};
  }
  h2 {
    ${styles.productiveHeading04};
  }
  h3 {
    ${styles.productiveHeading03};
  }
  h4 {
    ${styles.productiveHeading02};
  }
  p {
    ${styles.bodyLong02};
    margin: 0 0 var(--cds-spacing-04) 0;
    color: var(--cds-text-primary);
  }
  ul,
  ol {
    ${styles.bodyLong02};
    margin: 0 0 var(--cds-spacing-04) var(--cds-spacing-06);
    color: var(--cds-text-primary);
  }
  li {
    margin-bottom: var(--cds-spacing-02);
  }
  strong {
    font-weight: 600;
    color: var(--cds-text-primary);
  }
  code {
    ${styles.code01};
    background: var(--cds-layer-accent);
    padding: 0 var(--cds-spacing-02);
    border-radius: 2px;
  }
  > *:last-child {
    margin-bottom: 0;
  }
`;

// ---------------------------------------------------------------------------
// WidgetFrame — wrapper that reveals a "show config" action on hover, and
// expands an inline JSON panel below the widget when toggled.
// ---------------------------------------------------------------------------

const WidgetFrameContainer = styled.div`
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);

  /* Show the action bar on hover or when the panel is open. */
  &:hover .widget-actions {
    opacity: 1;
  }
`;

const WidgetActions = styled.div<{
  $visible: boolean;
}>`
  position: absolute;
  top: var(--cds-spacing-03);
  right: var(--cds-spacing-03);
  z-index: 1;
  opacity: ${({$visible}) => ($visible ? 1 : 0)};
  transition: opacity 120ms ease;
`;

/**
 * Modal-content typography for the widget details modal.
 * Description first (the LLM's natural-language summary), then the request
 * line, then the raw config JSON.
 */
const ConfigDescription = styled.p`
  ${styles.bodyLong02};
  color: var(--cds-text-primary);
  margin: 0 0 var(--cds-spacing-06) 0;
`;

const ConfigSectionLabel = styled.div`
  ${styles.label01};
  text-transform: uppercase;
  letter-spacing: 0.32px;
  color: var(--cds-text-secondary);
  margin-bottom: var(--cds-spacing-03);
`;

const ConfigEndpoint = styled.div`
  ${styles.code02};
  color: var(--cds-text-primary);
  word-break: break-all;
  background: var(--cds-layer-accent);
  padding: var(--cds-spacing-03) var(--cds-spacing-04);
  border-left: 3px solid var(--cds-support-info);
  border-radius: 2px;
  margin-bottom: var(--cds-spacing-05);
`;

const ConfigPanelBlock = styled.pre`
  ${styles.code01};
  color: var(--cds-text-primary);
  margin: 0;
  background: var(--cds-layer-accent);
  padding: var(--cds-spacing-04);
  border-radius: 2px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow-y: auto;
`;

/**
 * Editable JSON editor for the widget config modal. Same visual treatment as
 * ConfigPanelBlock so toggling between "view" and "edit" mode doesn't shift
 * the layout — only the underlying element (pre vs textarea) and the focus
 * affordances change.
 */
const ConfigEditorTextarea = styled.textarea`
  ${styles.code01};
  color: var(--cds-text-primary);
  background: var(--cds-layer-accent);
  border: 1px solid var(--cds-border-strong);
  border-radius: 2px;
  padding: var(--cds-spacing-04);
  width: 100%;
  min-height: 320px;
  resize: vertical;
  font-family: var(--cds-code-01-font-family, monospace);
  white-space: pre;
  overflow: auto;
  outline: none;

  &:focus {
    border-color: var(--cds-focus);
    box-shadow: 0 0 0 1px var(--cds-focus);
  }
`;

const ConfigEditorActions = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: var(--cds-spacing-03);
  margin-top: var(--cds-spacing-04);
`;

const ConfigEditorError = styled.div`
  ${styles.bodyShort01};
  color: var(--cds-text-error);
  background: var(--cds-notification-background-error);
  padding: var(--cds-spacing-03) var(--cds-spacing-04);
  border-left: 3px solid var(--cds-support-error);
  border-radius: 2px;
  margin-top: var(--cds-spacing-03);
  white-space: pre-wrap;
`;

// ---------------------------------------------------------------------------
// BpmnWidget — diagram container + overlay badges
// ---------------------------------------------------------------------------

/**
 * Legend rendered below the BPMN diagram explaining what the colors / badges
 * mean. Compact horizontal row of dot+label pairs.
 */
const BpmnLegend = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: var(--cds-spacing-05);
  padding: var(--cds-spacing-03) var(--cds-spacing-04) 0;
  ${styles.label02};
  color: var(--cds-text-secondary);
`;

const BpmnLegendItem = styled.span`
  display: inline-flex;
  align-items: center;
  gap: var(--cds-spacing-02);
`;

const BpmnLegendDot = styled.span<{$color: string; $intensity?: number}>`
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: ${({$color, $intensity}) =>
    $intensity != null ? `rgba(218, 30, 40, ${$intensity})` : $color};
`;

const BpmnLegendGradient = styled.span`
  display: inline-block;
  width: 80px;
  height: 10px;
  border-radius: 2px;
  background: linear-gradient(
    to right,
    rgba(218, 30, 40, 0.15),
    rgba(218, 30, 40, 0.9)
  );
`;

const BpmnDiagramContainer = styled.div`
  /* HERO tier — full-width, never sits horizontally next to a smaller widget,
     so this height doesn't need to match SHORT × N exactly. 480 reads well. */
  height: 480px;
  width: 100%;
  position: relative;
  overflow: hidden;
  border-radius: 2px;

  /* Force the bpmn-js canvas itself to fully fill — without this the canvas
     can render shorter than the container, leaving a hard cut at the bottom.
     The .djs-container is bpmn-js's root SVG host. */
  > div,
  .djs-container {
    height: 100% !important;
    width: 100% !important;
  }
`;

/**
 * Small count badge positioned absolutely inside the bpmn-js overlay container.
 * Use --cds-support-info for active (blue) and --cds-support-error for incidents (red).
 */
const OverlayBadge = styled.div<{$color: string}>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 var(--cds-spacing-02);
  background: ${({$color}) => $color};
  color: #fff;
  border-radius: 10px;
  ${styles.label01};
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  white-space: nowrap;
  pointer-events: none;
`;

const OverlayBadgeGroup = styled.div`
  display: flex;
  gap: 2px;
`;

// ---------------------------------------------------------------------------
// KpiWidget — horizontal grid of mini-metric cells inside one tile.
// ---------------------------------------------------------------------------

/**
 * Responsive grid that lays out KPI cells side by side.
 * auto-fit + minmax lets the grid reflow from 4-across to 2-across on narrow
 * containers without JS.
 */
const KpiGrid = styled.div`
  display: grid;
  /* Single-row layout: all cells share the available width. Using minmax(0, 1fr)
     means cells shrink instead of forcing the grid to wrap, which would push
     KPIs below the 296px TALL cap and clip them. */
  grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
  gap: var(--cds-spacing-05);
  margin-top: var(--cds-spacing-04);
`;

/**
 * Individual KPI cell: accent stripe + label + big number.
 * Mirrors the MetricTile feel at a smaller scale.
 */
const KpiCell = styled.div<{$accent?: string}>`
  position: relative;
  padding: var(--cds-spacing-03) var(--cds-spacing-04) var(--cds-spacing-03)
    calc(var(--cds-spacing-04) + 3px + var(--cds-spacing-03));
  background: var(--cds-layer-accent);
  border-radius: 2px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--cds-spacing-02);
  overflow: hidden;
  min-width: 0;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    bottom: 0;
    width: 3px;
    background: ${({$accent = 'info'}) =>
      ACCENT_COLOR[$accent] ?? ACCENT_COLOR['info']};
  }
`;

// ---------------------------------------------------------------------------
// StatusGridWidget — per-process health board
// ---------------------------------------------------------------------------

const StatusGridContainer = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--cds-spacing-04);
  margin-top: var(--cds-spacing-04);
`;

const StatusTile = styled.div<{$status: 'healthy' | 'warning' | 'critical'}>`
  position: relative;
  padding: var(--cds-spacing-04) var(--cds-spacing-04) var(--cds-spacing-04)
    calc(var(--cds-spacing-04) + 4px);
  background: var(--cds-layer-accent);
  border-radius: 2px;
  overflow: hidden;
  transition:
    transform 120ms ease,
    box-shadow 120ms ease;
  cursor: default;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    bottom: 0;
    width: 4px;
    background: ${({$status}) =>
      $status === 'healthy'
        ? 'var(--cds-support-success)'
        : $status === 'warning'
          ? 'var(--cds-support-warning)'
          : 'var(--cds-support-error)'};
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 3px 8px rgba(0, 0, 0, 0.1);
  }
`;

const StatusTileName = styled.div`
  ${styles.productiveHeading01};
  color: var(--cds-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const StatusTileVersion = styled.div`
  ${styles.label01};
  color: var(--cds-text-secondary);
  margin-top: var(--cds-spacing-02);
`;

const StatusTileCount = styled.div<{
  $status: 'healthy' | 'warning' | 'critical';
}>`
  ${styles.productiveHeading04};
  color: ${({$status}) =>
    $status === 'healthy'
      ? 'var(--cds-support-success)'
      : $status === 'warning'
        ? 'var(--cds-support-warning)'
        : 'var(--cds-support-error)'};
  margin-top: var(--cds-spacing-03);
  font-variant-numeric: tabular-nums;
`;

const StatusTileSkeleton = styled.div`
  background: var(--cds-layer-accent);
  border-radius: 2px;
  height: 80px;
  animation: pulse 1.5s ease-in-out infinite;

  @keyframes pulse {
    0%,
    100% {
      opacity: 1;
    }
    50% {
      opacity: 0.5;
    }
  }
`;

// ---------------------------------------------------------------------------
// ActivityFeedWidget — vertical timeline of events
// ---------------------------------------------------------------------------

const ActivityFeed = styled.div<{$scrollable?: boolean}>`
  display: flex;
  flex-direction: column;
  margin-top: var(--cds-spacing-04);
  position: relative;
  ${({$scrollable}) =>
    $scrollable
      ? `
    flex: 1;
    overflow-y: auto;
    min-height: 0;
  `
      : ''}
`;

const ActivityRow = styled.div`
  display: flex;
  gap: var(--cds-spacing-04);
  padding-bottom: var(--cds-spacing-04);
  position: relative;

  &:last-child {
    padding-bottom: 0;
  }

  /* Vertical connecting line between dots */
  &:not(:last-child)::after {
    content: '';
    position: absolute;
    left: 7px;
    top: 16px;
    bottom: 0;
    width: 2px;
    background: var(--cds-border-subtle);
  }
`;

const ActivityDot = styled.div<{$color?: string}>`
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: ${({$color}) => $color ?? 'var(--cds-support-info)'};
  margin-top: 2px;
  z-index: 1;
`;

const ActivityContent = styled.div`
  flex: 1;
  min-width: 0;
`;

const ActivityTitle = styled.div`
  ${styles.productiveHeading01};
  color: var(--cds-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const ActivitySubtitle = styled.div`
  ${styles.bodyShort01};
  color: var(--cds-text-secondary);
  margin-top: var(--cds-spacing-01);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const ActivityTime = styled.div`
  ${styles.label01};
  color: var(--cds-text-helper);
  margin-top: var(--cds-spacing-01);
  white-space: nowrap;
`;

// ---------------------------------------------------------------------------
// SparklineWrapper — small bottom margin below MetricValue
// ---------------------------------------------------------------------------

const SparklineWrapper = styled.div`
  margin-top: var(--cds-spacing-03);
`;

// ---------------------------------------------------------------------------
// TrendWidget — sparkline tile with label and bucket captions
// ---------------------------------------------------------------------------

/**
 * Compact sparkline slot inside the metric-tile-shaped TrendWidget.
 * Sits flush below the big value (parent flex gap handles spacing). The SVG
 * is height: 24px so the trend tile's three flex children still fit within
 * MetricTile's 132px min-height.
 */
const TrendSparklineArea = styled.div`
  display: flex;
  align-items: center;
  margin: 0;

  svg {
    display: block;
    max-width: 100%;
  }
`;

// ---------------------------------------------------------------------------
// FunnelWidget — horizontal bar funnel
// ---------------------------------------------------------------------------

const FunnelContainer = styled.div`
  display: flex;
  flex-direction: column;
  /* Compact gap so 4 stages (bar + inline drop-off) fit within the 296px TALL
     tile. Target: 4 × ~46px content + 3 × 4px gaps + 56px chrome ≈ 260px. */
  gap: var(--cds-spacing-02);
  margin-top: var(--cds-spacing-03);
  width: 100%;
  min-width: 0;
`;

const FunnelRow = styled.div`
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: var(--cds-spacing-03);
  width: 100%;
  min-width: 0;
`;

const FunnelBarTrack = styled.div`
  position: relative;
  /* Reduced from 32px to 28px so 4 stages fit cleanly in the 296px TALL tier
     without overflow. Label and count remain readable at this height. */
  height: 28px;
  flex: 1;
  background: var(--cds-layer-accent);
  border-radius: 2px;
  overflow: hidden;
`;

const FunnelBarFill = styled.div<{$pct: number; $colorIdx: number}>`
  height: 100%;
  width: ${({$pct}) => $pct}%;
  background: ${({$colorIdx}) => {
    const colors = ['var(--cds-support-info)', '#0353e9', '#0043ce', '#002d9c'];
    return colors[$colorIdx % colors.length];
  }};
  border-radius: 2px;
  transition: width 400ms ease;
`;

const FunnelBarLabel = styled.div`
  position: absolute;
  top: 50%;
  left: var(--cds-spacing-04);
  transform: translateY(-50%);
  ${styles.productiveHeading01};
  color: #fff;
  pointer-events: none;
`;

const FunnelBarCount = styled.div`
  position: absolute;
  top: 50%;
  right: var(--cds-spacing-04);
  transform: translateY(-50%);
  ${styles.label01};
  color: var(--cds-text-secondary);
  pointer-events: none;
`;

const FunnelDropoff = styled.div`
  ${styles.label01};
  color: var(--cds-text-helper);
  /* Inline next to the bar track. Fixed width keeps bars vertically aligned
     regardless of whether a drop-off is shown. Wide enough for "↓ 100% drop-off"
     without clipping. */
  flex-shrink: 0;
  width: 96px;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

// ---------------------------------------------------------------------------
// BPMN heatmap global styles
// ---------------------------------------------------------------------------

/**
 * Heatmap styles need to be GLOBAL because the .djs-element nodes are
 * rendered inside the bpmn-js canvas (a sibling subtree of our wrapper),
 * and styled-components scoping would never reach them. createGlobalStyle
 * injects the CSS into <head> with no scope class.
 *
 * Five intensity buckets targeting the visible SVG shape on each flow node.
 */
const BpmnHeatmapStyles = createGlobalStyle`
  .djs-element.notebook-heatmap-0 .djs-visual > :first-child {
    fill: rgba(218, 30, 40, 0.18) !important;
    stroke: rgba(218, 30, 40, 0.6) !important;
  }
  .djs-element.notebook-heatmap-1 .djs-visual > :first-child {
    fill: rgba(218, 30, 40, 0.35) !important;
    stroke: rgba(218, 30, 40, 0.7) !important;
  }
  .djs-element.notebook-heatmap-2 .djs-visual > :first-child {
    fill: rgba(218, 30, 40, 0.55) !important;
    stroke: rgba(218, 30, 40, 0.8) !important;
  }
  .djs-element.notebook-heatmap-3 .djs-visual > :first-child {
    fill: rgba(218, 30, 40, 0.75) !important;
    stroke: rgba(165, 14, 14, 1) !important;
  }
  .djs-element.notebook-heatmap-4 .djs-visual > :first-child {
    fill: rgba(218, 30, 40, 0.92) !important;
    stroke: rgba(165, 14, 14, 1) !important;
    stroke-width: 2px !important;
  }
`;

export {
  PageContainer,
  ContentScroll,
  NotebookTitle,
  NotebookHeader,
  WidgetsGrid,
  WidgetSlot,
  PromptSection,
  PromptSectionTitle,
  PromptSectionHint,
  PromptRow,
  PromptSuggestions,
  WidgetTitle,
  WidgetSubtitle,
  WidgetDivider,
  CircularChartWrap,
  WidgetTable,
  EmptyState,
  MetricTile,
  MetricCaption,
  MetricValue,
  MetricSubvalue,
  WidgetFrameContainer,
  WidgetActions,
  ConfigDescription,
  ConfigSectionLabel,
  ConfigEndpoint,
  ConfigPanelBlock,
  ConfigEditorTextarea,
  ConfigEditorActions,
  ConfigEditorError,
  BpmnDiagramContainer,
  BpmnLegend,
  BpmnLegendItem,
  BpmnLegendDot,
  BpmnLegendGradient,
  OverlayBadge,
  OverlayBadgeGroup,
  TextWidgetContainer,
  KpiGrid,
  KpiCell,
  ACCENT_COLOR,
  StatusGridContainer,
  StatusTile,
  StatusTileName,
  StatusTileVersion,
  StatusTileCount,
  StatusTileSkeleton,
  ActivityFeed,
  ActivityRow,
  ActivityDot,
  ActivityContent,
  ActivityTitle,
  ActivitySubtitle,
  ActivityTime,
  SparklineWrapper,
  TrendSparklineArea,
  FunnelContainer,
  FunnelRow,
  FunnelBarTrack,
  FunnelBarFill,
  FunnelBarLabel,
  FunnelBarCount,
  FunnelDropoff,
  BpmnHeatmapStyles,
};
