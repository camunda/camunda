/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import {IconButton, Modal} from '@carbon/react';
import {Code, TrashCan} from '@carbon/react/icons';
import type {WidgetConfig} from './types';
import {
  WidgetFrameContainer,
  WidgetActions,
  ConfigDescription,
  ConfigSectionLabel,
  ConfigPanelBlock,
} from './styled';

type Props = {
  config: WidgetConfig;
  children: React.ReactNode;
  onRemove?: () => void;
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
 * toggle). When toggled, a Carbon Modal opens showing the LLM-authored
 * description first, then the resolved HTTP request, then the raw
 * WidgetConfig JSON for the curious.
 */
const WidgetFrame: React.FC<Props> = ({
  config,
  children,
  onRemove,
  hideDetails = false,
}) => {
  const [showDetails, setShowDetails] = useState(false);

  return (
    <WidgetFrameContainer>
      <WidgetActions className="widget-actions" $visible={showDetails}>
        {!hideDetails && (
          <IconButton
            kind="ghost"
            size="sm"
            align="left"
            label="Show widget details"
            onClick={() => setShowDetails(true)}
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

      {showDetails && (
        <Modal
          open
          modalHeading={config.title}
          modalLabel="Widget details"
          passiveModal
          onRequestClose={() => setShowDetails(false)}
          size="md"
        >
          <ConfigDescription>
            {config.description ?? PLACEHOLDER_DESCRIPTION}
          </ConfigDescription>

          {shouldShowConfigJson(config) && (
            <>
              <ConfigSectionLabel>Generated config</ConfigSectionLabel>
              <ConfigPanelBlock>
                {JSON.stringify(presentableConfig(config), null, 2)}
              </ConfigPanelBlock>
            </>
          )}
        </Modal>
      )}
    </WidgetFrameContainer>
  );
};

export {WidgetFrame};
