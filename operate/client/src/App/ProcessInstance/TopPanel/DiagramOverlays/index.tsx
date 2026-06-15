/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {StateOverlay} from 'modules/components/StateOverlay';
import {WaitingStateOverlay} from 'modules/components/WaitingStateOverlay';
import {ModificationBadgeOverlay} from '../ModificationBadgeOverlay';
import {AgentStatusOverlay} from '../AgentStatusOverlay';
import {AgentShineOverlay} from '../AgentShineOverlay';
import {useModificationsByElement} from 'modules/hooks/modifications';
import {hasPendingCancelOrMoveModification} from 'modules/utils/modifications';
import {
  OVERLAY_TYPE_STATE,
  OVERLAY_TYPE_MODIFICATIONS_BADGE,
  OVERLAY_TYPE_WAITING_STATE,
  OVERLAY_TYPE_AGENT_STATUS,
  OVERLAY_TYPE_AGENT_SHINE,
  isStatisticsPayload,
  isModificationBadgePayload,
  isWaitingStatePayload,
  isAgentStatusPayload,
  isAgentShinePayload,
} from 'modules/bpmn-js/overlayTypes';

const DiagramOverlays: React.FC = observer(() => {
  const modificationsByElement = useModificationsByElement();
  const {overlays} = diagramOverlaysStore.state;

  const stateOverlays = overlays.filter(
    ({type}) => type === OVERLAY_TYPE_STATE,
  );
  const modificationBadgeOverlays = overlays.filter(
    ({type}) => type === OVERLAY_TYPE_MODIFICATIONS_BADGE,
  );
  const waitingOverlays = overlays.filter(
    ({type}) => type === OVERLAY_TYPE_WAITING_STATE,
  );
  const agentStatusOverlays = overlays.filter(
    ({type}) => type === OVERLAY_TYPE_AGENT_STATUS,
  );
  const agentShineOverlays = overlays.filter(
    ({type}) => type === OVERLAY_TYPE_AGENT_SHINE,
  );

  return (
    <>
      {stateOverlays.map((overlay) => {
        const payload = overlay.payload;
        if (!isStatisticsPayload(payload)) {
          return null;
        }

        return (
          <StateOverlay
            key={`${overlay.elementId}-${payload.elementState}`}
            state={payload.elementState}
            count={payload.count}
            container={overlay.container}
            isFaded={hasPendingCancelOrMoveModification({
              elementId: overlay.elementId,
              elementInstanceKey: undefined,
              modificationsByElement,
            })}
            title={
              payload.elementState === 'completed'
                ? 'Execution Count'
                : undefined
            }
          />
        );
      })}
      {modificationBadgeOverlays.map((overlay) => {
        const payload = overlay.payload;
        if (!isModificationBadgePayload(payload)) {
          return null;
        }

        return (
          <ModificationBadgeOverlay
            key={overlay.elementId}
            container={overlay.container}
            newTokenCount={payload.newTokenCount}
            cancelledTokenCount={payload.cancelledTokenCount}
          />
        );
      })}
      {waitingOverlays.map((overlay) => {
        const payload = overlay.payload;
        if (!isWaitingStatePayload(payload)) {
          return null;
        }

        return (
          <WaitingStateOverlay
            key={`waiting-${overlay.elementId}`}
            container={overlay.container}
            label={payload.label}
          />
        );
      })}
      {agentStatusOverlays.map((overlay) => {
        const payload = overlay.payload;
        if (!isAgentStatusPayload(payload)) {
          return null;
        }
        return (
          <AgentStatusOverlay
            key={`${payload.agentInstanceKey}-status`}
            container={overlay.container}
            status={payload.status}
          />
        );
      })}
      {agentShineOverlays.map((overlay) => {
        const payload = overlay.payload;
        if (!isAgentShinePayload(payload)) {
          return null;
        }
        return (
          <AgentShineOverlay
            key={`${payload.agentInstanceKey}-shine`}
            container={overlay.container}
            elementId={overlay.elementId}
          />
        );
      })}
    </>
  );
});

export {DiagramOverlays};
