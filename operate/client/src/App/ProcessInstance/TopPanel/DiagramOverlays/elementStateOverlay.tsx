/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* eslint-disable react-refresh/only-export-components -- overlay modules intentionally co-locate their data hook, config, and renderer in a single file */

import {useMemo} from 'react';
import {
  ACTIVE_BADGE,
  INCIDENTS_BADGE,
  CANCELED_BADGE,
  COMPLETED_BADGE,
  COMPLETED_END_EVENT_BADGE,
  SUBPROCESS_WITH_INCIDENTS,
} from 'modules/bpmn-js/badgePositions';
import {StateOverlay} from 'modules/components/StateOverlay';
import {executionCountToggleStore} from 'modules/stores/executionCountToggle';
import {useElementStatistics} from 'modules/queries/elementInstancesStatistics/useElementStatistics';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {useModificationsByElement} from 'modules/hooks/modifications';
import {hasPendingCancelOrMoveModification} from 'modules/utils/modifications';
import {getSubprocessOverlayFromIncidentElements} from 'modules/utils/elements';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useElementInstanceInspection} from 'modules/queries/elementInstanceInspection/useElementInstanceInspection';
import {isBeforeAllExecutionListenerWaitState} from 'modules/utils/waitStates';
import {getClientConfig} from 'modules/utils/getClientConfig';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import type {ElementState, OverlayData} from 'modules/bpmn-js/overlayTypes';
import type {DiagramOverlay} from './types';

const ELEMENT_STATE_OVERLAY_TYPE = 'elementState';

const overlayPositions = {
  active: ACTIVE_BADGE,
  incidents: INCIDENTS_BADGE,
  canceled: CANCELED_BADGE,
  completed: COMPLETED_BADGE,
  completedEndEvents: COMPLETED_END_EVENT_BADGE,
  subprocessWithIncidents: SUBPROCESS_WITH_INCIDENTS,
} as const;

type ElementStatePayload = {
  elementState: ElementState | 'completedEndEvents';
  count?: number;
};

const useElementStateOverlaysData = (): OverlayData[] => {
  const {data: statistics} = useElementStatistics();
  const {data: businessObjects} = useBusinessObjects();
  const {isExecutionCountVisible} = executionCountToggleStore.state;
  const clientConfig = getClientConfig();
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {data: processInstance} = useProcessInstance();
  const {data: inspectionData} = useElementInstanceInspection({
    processInstanceKey: processInstanceId,
    enabled:
      clientConfig.waitStatesEnabled && processInstance?.state === 'ACTIVE',
  });

  return useMemo(() => {
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
        payload: {elementState, count},
        type: ELEMENT_STATE_OVERLAY_TYPE,
        elementId,
        position: overlayPositions[elementState],
      })) || []),
      ...subprocessOverlays,
    ];

    if (inspectionData?.items?.length) {
      const elementIdsInStats = new Set(statistics?.map(({id}) => id) ?? []);
      for (const item of inspectionData.items) {
        if (
          isBeforeAllExecutionListenerWaitState(item) &&
          !elementIdsInStats.has(item.elementId)
        ) {
          allElementStateOverlays.push({
            payload: {elementState: 'active' as const},
            type: ELEMENT_STATE_OVERLAY_TYPE,
            elementId: item.elementId,
            position: overlayPositions.active,
          });
        }
      }
    }

    const notCompletedElementStateOverlays = allElementStateOverlays.filter(
      (stateOverlay) => stateOverlay.payload.elementState !== 'completed',
    );

    return isExecutionCountVisible
      ? allElementStateOverlays
      : notCompletedElementStateOverlays;
  }, [statistics, businessObjects, isExecutionCountVisible, inspectionData]);
};

const ElementStateOverlay: React.FC<{overlay: DiagramOverlay}> = ({
  overlay,
}) => {
  const modificationsByElement = useModificationsByElement();
  const payload = overlay.payload as ElementStatePayload;

  return (
    <StateOverlay
      state={payload.elementState}
      count={payload.count}
      container={overlay.container}
      isFaded={hasPendingCancelOrMoveModification({
        elementId: overlay.elementId,
        elementInstanceKey: undefined,
        modificationsByElement,
      })}
      title={
        payload.elementState === 'completed' ? 'Execution Count' : undefined
      }
    />
  );
};

const getElementStateOverlayKey = (overlay: DiagramOverlay): string =>
  `${overlay.elementId}-${(overlay.payload as ElementStatePayload).elementState}`;

export {
  ELEMENT_STATE_OVERLAY_TYPE,
  useElementStateOverlaysData,
  ElementStateOverlay,
  getElementStateOverlayKey,
};
