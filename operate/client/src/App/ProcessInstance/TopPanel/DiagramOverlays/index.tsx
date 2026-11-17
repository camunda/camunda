/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {
  ELEMENT_STATE_OVERLAY_TYPE,
  ElementStateOverlay,
  getElementStateOverlayKey,
} from './elementStateOverlay';
import {
  MODIFICATION_BADGE_OVERLAY_TYPE,
  ModificationBadgeOverlay,
  getModificationBadgeOverlayKey,
} from './modificationBadgeOverlay';
import {
  WAITING_STATE_OVERLAY_TYPE,
  WaitingStateOverlay,
  getWaitingStateOverlayKey,
} from './waitingStateOverlay';
import {
  AGENT_STATUS_OVERLAY_TYPE,
  AgentStatusOverlay,
  getAgentStatusOverlayKey,
} from './agentStatusOverlay';
import {
  AGENT_SHINE_OVERLAY_TYPE,
  AgentShineOverlay,
  getAgentShineOverlayKey,
} from './agentShineOverlay';

/**
 * Renders every overlay currently in the {@link diagramOverlaysStore} by wiring
 * each overlay type to its renderer. To add a new overlay type, add a case here
 * and build its data in `./useDiagramOverlaysData.ts`.
 */
const DiagramOverlays: React.FC = observer(() => {
  const {overlays} = diagramOverlaysStore.state;

  return (
    <>
      {overlays.map((overlay) => {
        switch (overlay.type) {
          case ELEMENT_STATE_OVERLAY_TYPE:
            return (
              <ElementStateOverlay
                key={getElementStateOverlayKey(overlay)}
                overlay={overlay}
              />
            );
          case MODIFICATION_BADGE_OVERLAY_TYPE:
            return (
              <ModificationBadgeOverlay
                key={getModificationBadgeOverlayKey(overlay)}
                overlay={overlay}
              />
            );
          case WAITING_STATE_OVERLAY_TYPE:
            return (
              <WaitingStateOverlay
                key={getWaitingStateOverlayKey(overlay)}
                overlay={overlay}
              />
            );
          case AGENT_STATUS_OVERLAY_TYPE:
            return (
              <AgentStatusOverlay
                key={getAgentStatusOverlayKey(overlay)}
                overlay={overlay}
              />
            );
          case AGENT_SHINE_OVERLAY_TYPE:
            return (
              <AgentShineOverlay
                key={getAgentShineOverlayKey(overlay)}
                overlay={overlay}
              />
            );
          default:
            return null;
        }
      })}
    </>
  );
});

export {DiagramOverlays};
