/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {computed} from 'mobx';
import {type OverlayPosition} from 'bpmn-js/lib/NavigatedViewer';
import {
  CANCELED_BADGE,
  MODIFICATIONS,
  ACTIVE_BADGE,
  INCIDENTS_BADGE,
  COMPLETED_BADGE,
  COMPLETED_END_EVENT_BADGE,
  SUBPROCESS_WITH_INCIDENTS,
  WAITING_BADGE,
  AGENT_STATUS_TAG,
  AGENT_SHINE,
} from 'modules/bpmn-js/badgePositions';
import {modificationsStore} from 'modules/stores/modifications';
import {executionCountToggleStore} from 'modules/stores/executionCountToggle';
import {useProcessInstancePageParams} from '../useProcessInstancePageParams';
import {useElementStatistics} from 'modules/queries/elementInstancesStatistics/useElementStatistics';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useElementInstanceInspection} from 'modules/queries/elementInstanceInspection/useElementInstanceInspection';
import {useProcessInstanceAgentInstances} from 'modules/queries/agentInstances/useProcessInstanceAgentInstances';
import {useModificationsByElement} from 'modules/hooks/modifications';
import {getSubprocessOverlayFromIncidentElements} from 'modules/utils/elements';
import {getWaitStateLabel} from 'modules/utils/waitStates';
import {
  OVERLAY_TYPE_STATE,
  OVERLAY_TYPE_MODIFICATIONS_BADGE,
  OVERLAY_TYPE_WAITING_STATE,
  OVERLAY_TYPE_AGENT_STATUS,
  OVERLAY_TYPE_AGENT_SHINE,
  type AgentShinePayload,
  type AgentStatusPayload,
  type ModificationBadgePayload,
  type OverlayData,
} from 'modules/bpmn-js/overlayTypes';
import {getClientConfig} from 'modules/utils/getClientConfig';

const overlayPositions = {
  active: ACTIVE_BADGE,
  incidents: INCIDENTS_BADGE,
  canceled: CANCELED_BADGE,
  completed: COMPLETED_BADGE,
  completedEndEvents: COMPLETED_END_EVENT_BADGE,
  subprocessWithIncidents: SUBPROCESS_WITH_INCIDENTS,
} as const;

function useDiagramOverlaysData(): OverlayData[] {
  const clientConfig = getClientConfig();
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {data: statistics} = useElementStatistics();
  const {data: businessObjects} = useBusinessObjects();
  const {isExecutionCountVisible} = executionCountToggleStore.state;
  const {data: processInstance} = useProcessInstance();
  const {data: inspectionData} = useElementInstanceInspection({
    processInstanceKey: processInstanceId,
    enabled:
      clientConfig.waitStatesEnabled && processInstance?.state === 'ACTIVE',
  });
  const {data: agentInstancesData} = useProcessInstanceAgentInstances();
  const modificationsByElement = useModificationsByElement();
  const {isModificationModeEnabled} = modificationsStore;

  const elementStateOverlays = useMemo(() => {
    const elementIdsWithIncidents = statistics
      ?.filter(({elementState}) => elementState === 'incidents')
      ?.map((element) => element.id);

    const selectableElementsWithIncidents = elementIdsWithIncidents?.map(
      (elementId) => businessObjects?.[elementId],
    );

    const subprocessOverlays = getSubprocessOverlayFromIncidentElements(
      selectableElementsWithIncidents,
    );

    const allElementStateOverlays = [
      ...(statistics?.map(({elementState, count, id: elementId}) => ({
        payload: {elementState: elementState, count},
        type: OVERLAY_TYPE_STATE,
        elementId,
        position: overlayPositions[elementState],
      })) || []),
      ...subprocessOverlays,
    ];

    const notCompletedElementStateOverlays = allElementStateOverlays?.filter(
      (stateOverlay) => stateOverlay.payload.elementState !== 'completed',
    );

    return isExecutionCountVisible
      ? allElementStateOverlays
      : notCompletedElementStateOverlays;
  }, [statistics, businessObjects, isExecutionCountVisible]);

  const allWaitingStateOverlays = useMemo(() => {
    if (!inspectionData?.items?.length) {
      return [];
    }

    const waitStatesByElement = new Map<string, typeof inspectionData.items>();
    for (const item of inspectionData.items) {
      const existing = waitStatesByElement.get(item.elementId) ?? [];
      existing.push(item);
      waitStatesByElement.set(item.elementId, existing);
    }

    const overlays: Array<{
      elementId: string;
      type: string;
      position: typeof WAITING_BADGE;
      payload: {label: string};
    }> = [];

    for (const [elementId, waitStates] of waitStatesByElement) {
      const label = getWaitStateLabel(waitStates);
      if (label) {
        overlays.push({
          elementId,
          type: OVERLAY_TYPE_WAITING_STATE,
          position: WAITING_BADGE,
          payload: {label},
        });
      }
    }

    return overlays;
  }, [inspectionData]);

  const {agentOverlays, elementsWithAgent} = useMemo(() => {
    if (!agentInstancesData?.items?.length) {
      return {agentOverlays: [], elementsWithAgent: new Set<string>()};
    }

    const elementsWithAgent = new Set<string>();

    const agentOverlays = agentInstancesData.items.flatMap<OverlayData>(
      (agentInstance) => {
        // We expect only one active agent instance per element. But there *can* be multiple.
        // For now, only add an overlay to an element for first matching agent instance.
        if (elementsWithAgent.has(agentInstance.elementId)) {
          return [];
        }

        elementsWithAgent.add(agentInstance.elementId);
        return [
          {
            type: OVERLAY_TYPE_AGENT_STATUS,
            elementId: agentInstance.elementId,
            position: AGENT_STATUS_TAG,
            payload: {
              status: agentInstance.status,
              agentInstanceKey: agentInstance.agentInstanceKey,
            } satisfies AgentStatusPayload,
          },
          {
            type: OVERLAY_TYPE_AGENT_SHINE,
            elementId: agentInstance.elementId,
            position: AGENT_SHINE,
            payload: {
              agentInstanceKey: agentInstance.agentInstanceKey,
            } satisfies AgentShinePayload,
          },
        ];
      },
    );
    return {agentOverlays, elementsWithAgent};
  }, [agentInstancesData]);

  const waitingStateOverlays = useMemo(() => {
    return allWaitingStateOverlays.filter(
      (overlay) => !elementsWithAgent.has(overlay.elementId),
    );
  }, [allWaitingStateOverlays, elementsWithAgent]);

  const modificationBadgesPerElement = computed(() =>
    Object.entries(modificationsByElement).reduce<
      {
        elementId: string;
        type: string;
        payload: ModificationBadgePayload;
        position: OverlayPosition;
      }[]
    >((badges, [elementId, tokens]) => {
      return [
        ...badges,
        {
          elementId,
          type: OVERLAY_TYPE_MODIFICATIONS_BADGE,
          position: MODIFICATIONS,
          payload: {
            newTokenCount: tokens.newTokens,
            cancelledTokenCount: tokens.visibleCancelledTokens,
          },
        },
      ];
    }, []),
  );

  if (isModificationModeEnabled) {
    return [
      ...(elementStateOverlays ?? []),
      ...modificationBadgesPerElement.get(),
    ];
  }

  return [
    ...(elementStateOverlays ?? []),
    ...agentOverlays,
    ...waitingStateOverlays,
  ];
}

export {useDiagramOverlaysData};
