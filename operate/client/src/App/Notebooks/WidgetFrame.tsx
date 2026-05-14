/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import {Button, IconButton, Modal} from '@carbon/react';
import {Code, TrashCan} from '@carbon/react/icons';
import {WidgetConfigSchema, type WidgetConfig} from './types';
import {
  WidgetFrameContainer,
  WidgetActions,
  ConfigDescription,
  ConfigSectionLabel,
  ConfigPanelBlock,
  ConfigEditorTextarea,
  ConfigEditorActions,
  ConfigEditorError,
} from './styled';

type Props = {
  config: WidgetConfig;
  children: React.ReactNode;
  onRemove?: () => void;
  /**
   * Called when the user saves an edited config from the details modal. The
   * incoming `next` is already validated against `WidgetConfigSchema`, with
   * `id` preserved from the original.
   */
  onUpdate?: (next: WidgetConfig) => void;
  // When true, the "show details" (</>) action and modal are skipped. Used
  // for narrative widgets (text) where the markdown is the content and the
  // config JSON has no useful information.
  hideDetails?: boolean;
};

const PLACEHOLDER_DESCRIPTION = 'No description was provided for this widget.';

/**
 * Strip internal-only placeholder fields from a config before showing it in
 * the details modal. The schema requires a `query` even for widgets that
 * don't fetch (text, kpi) — but exposing "/__notebook_kpi__" to users makes
 * the widget look broken. We rewrite/remove these fields so the modal shows
 * only meaningful information.
 */
function presentableConfig(config: WidgetConfig): unknown {
  const cloned: Record<string, unknown> = {...config};
  if (
    typeof cloned.query === 'object' &&
    cloned.query !== null &&
    'endpoint' in cloned.query &&
    typeof (cloned.query as {endpoint?: unknown}).endpoint === 'string' &&
    ((cloned.query as {endpoint: string}).endpoint as string).startsWith(
      '/__notebook_',
    )
  ) {
    delete cloned.query;
  }
  return cloned;
}

/**
 * Text widgets are pure markdown — the JSON has no useful information
 * beyond what the prose already says, so we hide the config block. Every
 * other widget type (including kpi, which has multiple inner queries) shows
 * its config so the audience can see exactly which V2 endpoints are called.
 */
function shouldShowConfigJson(config: WidgetConfig): boolean {
  return config.type !== 'text';
}

/**
 * Wraps a widget with a hover-revealed action bar (currently: a "Show details"
 * toggle, an "Edit config" toggle, and a remove button). Selecting either of
 * the first two opens a Carbon Modal showing the LLM-authored description
 * first, then the config JSON — read-only by default, editable when the user
 * clicks the pencil icon (or chose "Edit config" directly).
 */
const WidgetFrame: React.FC<Props> = ({
  config,
  children,
  onRemove,
  onUpdate,
  hideDetails = false,
}) => {
  const [modalState, setModalState] = useState<'closed' | 'view' | 'edit'>(
    'closed',
  );
  const [draft, setDraft] = useState<string>('');
  const [error, setError] = useState<string | null>(null);

  const openView = () => {
    setModalState('view');
    setError(null);
  };
  const openEdit = () => {
    setDraft(JSON.stringify(presentableConfig(config), null, 2));
    setModalState('edit');
    setError(null);
  };
  const close = () => {
    setModalState('closed');
    setError(null);
  };

  const handleSave = () => {
    if (onUpdate == null) {
      return;
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(draft);
    } catch (err) {
      setError(
        err instanceof Error ? `Invalid JSON: ${err.message}` : 'Invalid JSON.',
      );
      return;
    }

    // Preserve the original id — editing should never silently re-key a
    // widget (would break React reconciliation and any saved layout).
    //
    // Also restore the original `query` when it was stripped by
    // `presentableConfig` (placeholder queries on kpi/text widgets are hidden
    // from the user). The schema requires `query` for every widget; without
    // this restore the user would be forced to invent a fake endpoint URL.
    const parsedObj =
      parsed != null && typeof parsed === 'object'
        ? (parsed as Record<string, unknown>)
        : null;
    let merged: unknown = parsed;
    if (parsedObj != null) {
      const next: Record<string, unknown> = {...parsedObj, id: config.id};
      if (next.query == null) {
        next.query = config.query;
      }
      merged = next;
    }

    const result = WidgetConfigSchema.safeParse(merged);
    if (!result.success) {
      const issues = result.error.issues
        .slice(0, 5)
        .map((i) => `• ${i.path.join('.') || '(root)'}: ${i.message}`)
        .join('\n');
      setError(`Invalid widget config:\n${issues}`);
      return;
    }

    onUpdate(result.data);
    close();
  };

  const isEditable = onUpdate != null && shouldShowConfigJson(config);

  return (
    <WidgetFrameContainer>
      <WidgetActions
        className="widget-actions"
        $visible={modalState !== 'closed'}
      >
        {!hideDetails && (
          <IconButton
            kind="ghost"
            size="sm"
            align="left"
            label="Show widget details"
            onClick={openView}
          >
            <Code />
          </IconButton>
        )}
        {onRemove && (
          <IconButton
            kind="ghost"
            size="sm"
            align="left"
            label="Remove widget"
            onClick={onRemove}
          >
            <TrashCan />
          </IconButton>
        )}
      </WidgetActions>

      {children}

      {modalState !== 'closed' && (
        <Modal
          open
          modalHeading={config.title}
          modalLabel={modalState === 'edit' ? 'Edit widget' : 'Widget details'}
          passiveModal
          onRequestClose={close}
          size="md"
        >
          <ConfigDescription>
            {config.description ?? PLACEHOLDER_DESCRIPTION}
          </ConfigDescription>

          {shouldShowConfigJson(config) && (
            <>
              <ConfigSectionLabel>
                {modalState === 'edit'
                  ? 'Edit config (JSON)'
                  : 'Generated config'}
              </ConfigSectionLabel>

              {modalState === 'view' && (
                <>
                  <ConfigPanelBlock>
                    {JSON.stringify(presentableConfig(config), null, 2)}
                  </ConfigPanelBlock>
                  {isEditable && (
                    <ConfigEditorActions>
                      <Button kind="tertiary" size="sm" onClick={openEdit}>
                        Edit
                      </Button>
                    </ConfigEditorActions>
                  )}
                </>
              )}

              {modalState === 'edit' && (
                <>
                  <ConfigEditorTextarea
                    value={draft}
                    onChange={(e) => setDraft(e.target.value)}
                    spellCheck={false}
                    aria-label="Widget config JSON"
                  />
                  {error && <ConfigEditorError>{error}</ConfigEditorError>}
                  <ConfigEditorActions>
                    <Button kind="ghost" size="sm" onClick={openView}>
                      Cancel
                    </Button>
                    <Button kind="primary" size="sm" onClick={handleSave}>
                      Save
                    </Button>
                  </ConfigEditorActions>
                </>
              )}
            </>
          )}
        </Modal>
      )}
    </WidgetFrameContainer>
  );
};

export {WidgetFrame};
