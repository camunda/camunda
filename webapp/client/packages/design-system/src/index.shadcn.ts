/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Mode CSS — loaded as side effects so consumers don't manage stylesheets
// manually. `theme.scss` ships Carbon's CSS tokens + IBM Plex fonts (always
// needed for `var(--cds-*)` references in styled-components). `globals.css`
// ships shadcn's neutral palette + Tailwind layer setup. `carbon-navigation.scss`
// loads the Carbon component CSS rules required by C3Navigation (which is built
// on Carbon primitives in @camunda/camunda-composite-components). Switching to
// carbon mode is a one-line edit in `src/index.ts`.
import '../theme.scss';
import '../globals.css';
import '../carbon-navigation.scss';

export * from './components/ThemeProvider';
export * from './components/ui/header/theme.carbon';

// Carbon-API adapters — same identifier names as `index.carbon.ts`, backed by
// shadcn primitives. Flipping `index.ts` from `./index.carbon` to `./index.shadcn`
// keeps consumer imports compiling.
export * from './components/ui/actionable-notification/actionable-notification.adapter';
export * from './components/ui/breadcrumb/breadcrumb.adapter';
export * from './components/ui/button/button.adapter';
export * from './components/ui/callout/callout.adapter';
export * from './components/ui/checkbox/checkbox.adapter';
export * from './components/ui/combo-box/combo-box.adapter';
export * from './components/ui/date-picker/date-picker.adapter';
export * from './components/ui/dropdown/dropdown.adapter';
export * from './components/ui/icon-button/icon-button.adapter';
export * from './components/ui/inline-loading/inline-loading.adapter';
export * from './components/ui/inline-notification/inline-notification.adapter';
export * from './components/ui/loading/loading.adapter';
export * from './components/ui/menu-button/menu-button.adapter';
export * from './components/ui/modal/modal.adapter';
export * from './components/ui/overflow-menu/overflow-menu.adapter';
export * from './components/ui/pagination/pagination.adapter';
export * from './components/ui/popover/popover.adapter';
export * from './components/ui/select/select.adapter';
export * from './components/ui/skeleton-icon/skeleton-icon.adapter';
export * from './components/ui/skeleton-text/skeleton-text.adapter';
export * from './components/ui/structured-list/structured-list.adapter';
export * from './components/ui/table/table.adapter';
export * from './components/ui/tabs/tabs.adapter';
export * from './components/ui/tag/tag.adapter';
export * from './components/ui/text-area/text-area.adapter';
export * from './components/ui/text-input/text-input.adapter';
export * from './components/ui/tile/tile.adapter';
export * from './components/ui/toast-notification/toast-notification.adapter';
export * from './components/ui/toggle/toggle.adapter';
export * from './components/ui/tooltip/tooltip.adapter';

// Wrappers backed by Camunda-specific custom-built primitives (no upstream
// shadcn equivalent). The adapter still applies for prop-drop boundaries.
export * from './components/ui/code-snippet/code-snippet.adapter';
export * from './components/ui/column/column.adapter';
export * from './components/ui/contained-list/contained-list.adapter';
export * from './components/ui/grid/grid.adapter';
export * from './components/ui/header/header.adapter';
export * from './components/ui/heading/heading.adapter';
export * from './components/ui/layer/layer.adapter';
export * from './components/ui/link/link.adapter';
export * from './components/ui/multi-select/multi-select.adapter';
export * from './components/ui/section/section.adapter';
export * from './components/ui/stack/stack.adapter';
export * from './components/ui/tree-view/tree-view.adapter';
export * from './components/ui/unordered-list/unordered-list.adapter';

// Migrated from Carbon — full table family backed by shadcn primitives.
export * from './components/ui/data-table/data-table.adapter';
export * from './components/ui/data-table-skeleton/data-table-skeleton.adapter';
export * from './components/ui/table-container/table-container.adapter';
export * from './components/ui/table-toolbar/table-toolbar.adapter';
export * from './components/ui/table-batch-actions/table-batch-actions.adapter';
export * from './components/ui/table-expand/table-expand.adapter';
export * from './components/ui/table-select/table-select.adapter';
export * from './components/ui/ordered-list/ordered-list.adapter';
export * from './components/ui/password-input/password-input.adapter';

// Carbon utility type re-export — `ButtonSize` has no shadcn equivalent and
// is consumed by Operate's Button-derived components.
export type {ButtonSize} from '@carbon/react';

// Canonical shadcn primitives — referenced internally by the adapters above
// and re-exported here for consumers who want the raw shadcn API.
export * from './components/ui/alert/alert.shadcn';
export * from './components/ui/alert-dialog/alert-dialog.shadcn';
export * from './components/ui/badge/badge.shadcn';
export * from './components/ui/calendar/calendar.shadcn';
export * from './components/ui/card/card.shadcn';
export * from './components/ui/dialog/dialog.shadcn';
export * from './components/ui/dropdown-menu/dropdown-menu.shadcn';
export * from './components/ui/input-group/input-group.shadcn';
export * from './components/ui/skeleton/skeleton.shadcn';
export * from './components/ui/sonner/sonner.shadcn';
export * from './components/ui/spinner/spinner.shadcn';
export * from './components/ui/switch/switch.shadcn';
