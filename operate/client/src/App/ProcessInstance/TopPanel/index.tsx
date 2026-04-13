/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo} from 'react';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from '../useProcessInstancePageParams';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {tracking} from 'modules/tracking';
import {modificationsStore} from 'modules/stores/modifications';
import {Container, DiagramPanel} from './styled';
import {
  CANCELED_BADGE,
  MODIFICATIONS,
  ACTIVE_BADGE,
  INCIDENTS_BADGE,
  COMPLETED_BADGE,
  COMPLETED_END_EVENT_BADGE,
  SUBPROCESS_WITH_INCIDENTS,
  AGENT_STATUS_TAG,
} from 'modules/bpmn-js/badgePositions';
import {DiagramShell} from 'modules/components/DiagramShell';
import {computed} from 'mobx';
import {type OverlayPosition} from 'bpmn-js/lib/NavigatedViewer';
import {Diagram} from 'modules/components/Diagram';
import {ModificationBadgeOverlay} from './ModificationBadgeOverlay';
import {ModificationInfoBanner} from './ModificationInfoBanner';
import {ModificationDropdown} from './ModificationDropdown';
import {StateOverlay} from 'modules/components/StateOverlay';
import {executionCountToggleStore} from 'modules/stores/executionCountToggle';
import {useAgentData} from 'modules/contexts/agentData';
import {AgentStatusOverlay} from './AgentStatusOverlay';
import {useElementStatistics} from 'modules/queries/elementInstancesStatistics/useElementStatistics';
import {useSelectableElements} from 'modules/queries/elementInstancesStatistics/useSelectableElements';
import {useExecutedElements} from 'modules/queries/elementInstancesStatistics/useExecutedElements';
import {useModificationsByElement} from 'modules/hooks/modifications';
import {useModifiableElements} from 'modules/hooks/processInstanceDetailsDiagram';
import {
  useTotalRunningInstancesByElement,
  useTotalRunningInstancesForElement,
  useTotalRunningInstancesVisibleForElement,
} from 'modules/queries/elementInstancesStatistics/useTotalRunningInstancesForElement';
import {
  finishMovingToken,
  hasPendingCancelOrMoveModification,
} from 'modules/utils/modifications';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {isCompensationAssociation} from 'modules/bpmn-js/utils/isCompensationAssociation';
import {useProcessSequenceFlows} from 'modules/queries/sequenceFlows/useProcessSequenceFlows';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {getSubprocessOverlayFromIncidentElements} from 'modules/utils/elements';
import type {ElementState} from 'modules/bpmn-js/overlayTypes';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {isRequestError} from 'modules/request';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useDrillDownNavigation} from 'modules/hooks/useDrilldownNavigation';
import {getAncestorScopeType} from 'modules/utils/processInstanceDetailsDiagram';

const OVERLAY_TYPE_STATE = 'elementState';
const OVERLAY_TYPE_MODIFICATIONS_BADGE = 'modificationsBadge';
const OVERLAY_TYPE_AGENT_STATUS = 'agentStatus';

const overlayPositions = {
  active: ACTIVE_BADGE,
  incidents: INCIDENTS_BADGE,
  canceled: CANCELED_BADGE,
  completed: COMPLETED_BADGE,
  completedEndEvents: COMPLETED_END_EVENT_BADGE,
  subprocessWithIncidents: SUBPROCESS_WITH_INCIDENTS,
} as const;

type ModificationBadgePayload = {
  newTokenCount: number;
  cancelledTokenCount: number;
};

const TopPanel: React.FC = observer(() => {
  const {
    clearSelection,
    selectedElementId,
    selectedElementInstanceKey,
    selectElement,
    selectedAnchorElementId,
  } = useProcessInstanceElementSelection();
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {
    sourceElementIdForMoveOperation,
    sourceElementInstanceKeyForMoveOperation,
  } = modificationsStore.state;
  const {data: statistics} = useElementStatistics();
  const {data: selectableElements} = useSelectableElements();
  const {data: executedElements} = useExecutedElements();
  const {data: totalRunningInstancesByElement} =
    useTotalRunningInstancesByElement();
  const {data: businessObjects} = useBusinessObjects();
  const {data: totalMoveOperationRunningInstances} =
    useTotalRunningInstancesForElement(
      sourceElementIdForMoveOperation || undefined,
    );
  const {data: totalMoveOperationRunningInstancesVisible} =
    useTotalRunningInstancesVisibleForElement(
      sourceElementIdForMoveOperation || undefined,
    );
  const {data: processInstance} = useProcessInstance();
  const modificationsByElement = useModificationsByElement();
  const affectedTokenCount = sourceElementInstanceKeyForMoveOperation
    ? 1
    : totalMoveOperationRunningInstances || 1;
  const visibleAffectedTokenCount = sourceElementInstanceKeyForMoveOperation
    ? 1
    : totalMoveOperationRunningInstancesVisible || 1;

  const {data: processedSequenceFlowsFromHook} =
    useProcessSequenceFlows(processInstanceId);
  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {isExecutionCountVisible} = executionCountToggleStore.state;
  const {isAgentInstance, getAgentStatusLabel} = useAgentData();

  const {data: selectedElementRunningInstancesCount} =
    useTotalRunningInstancesForElement(selectedElementId ?? undefined);
  const hasSelectedElementMultipleRunningInstances =
    selectedElementInstanceKey === null &&
    (selectedElementRunningInstancesCount ?? 0) > 1;

  const {
    data: processDefinitionData,
    isPending: isXmlFetching,
    isError: isXmlError,
    error: xmlError,
  } = useProcessInstanceXml({processDefinitionKey});

  useEffect(() => {
    return () => {
      diagramOverlaysStore.reset();
    };
  }, [processInstanceId]);

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

  const agentStatusLabel = isAgentInstance ? getAgentStatusLabel() : null;
  const agentStatusOverlays = useMemo(() => {
    if (!agentStatusLabel) return [];
    return [
      {
        payload: {label: agentStatusLabel},
        type: OVERLAY_TYPE_AGENT_STATUS,
        elementId: 'AI_Agent',
        position: AGENT_STATUS_TAG,
      },
    ];
  }, [agentStatusLabel]);

  const selectedElementIds = useMemo(() => {
    return selectedAnchorElementId
      ? [selectedAnchorElementId]
      : selectedElementId
        ? [selectedElementId]
        : undefined;
  }, [selectedElementId, selectedAnchorElementId]);

  const highlightedSequenceFlows = useMemo(() => {
    const compensationAssociationIds = Object.values(
      processDefinitionData?.diagramModel.elementsById ?? {},
    )
      .filter(isCompensationAssociation)
      .filter(({targetRef}) => {
        // check if the target element for the association was executed
        return executedElements?.find(({elementId, completed}) => {
          return targetRef?.id === elementId && completed > 0;
        });
      })
      .map(({id}) => id);

    return [
      ...(processedSequenceFlowsFromHook || []),
      ...compensationAssociationIds,
    ];
  }, [processedSequenceFlowsFromHook, processDefinitionData, executedElements]);

  const highlightedSequenceFlowIds = useMemo(() => {
    return executedElements?.map(({elementId}) => elementId);
  }, [executedElements]);

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

  const stateOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === OVERLAY_TYPE_STATE,
  );
  const modificationBadgeOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === OVERLAY_TYPE_MODIFICATIONS_BADGE,
  );
  const agentOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === OVERLAY_TYPE_AGENT_STATUS,
  );

  const modifiableElements = useModifiableElements();

  const {isModificationModeEnabled} = modificationsStore;

  const {handleDrillDown, pendingDrillDownElementId} =
    useDrillDownNavigation(processInstanceId);

  const customElementClasses = useMemo<
    [elementId: string, className: string][]
  >(() => {
    if (
      isModificationModeEnabled ||
      !businessObjects ||
      !totalRunningInstancesByElement
    ) {
      return [];
    }

    const DRILLDOWN_TYPES = ['bpmn:CallActivity', 'bpmn:BusinessRuleTask'];
    const drilldownClasses: [string, string][] = Object.entries(businessObjects)
      .filter(
        ([elementId, bo]) =>
          DRILLDOWN_TYPES.includes(bo.$type) &&
          (totalRunningInstancesByElement[elementId] ?? 0) > 0,
      )
      .map(([elementId]) => [elementId, 'op-drilldown'] as const);

    if (pendingDrillDownElementId !== null) {
      drilldownClasses.push([
        pendingDrillDownElementId,
        'op-drilldown-loading',
      ]);
    }

    return drilldownClasses;
  }, [
    businessObjects,
    totalRunningInstancesByElement,
    isModificationModeEnabled,
    pendingDrillDownElementId,
  ]);

  useEffect(() => {
    if (!isModificationModeEnabled) {
      if (selectedElementId) {
        tracking.track({eventName: 'metadata-popover-opened'});
      } else {
        tracking.track({eventName: 'metadata-popover-closed'});
      }
    }
  }, [isModificationModeEnabled, selectedElementId]);

  const getStatus = () => {
    if (isXmlFetching) {
      return 'loading';
    }
    if (
      isRequestError(xmlError) &&
      xmlError?.response?.status === HTTP_STATUS_FORBIDDEN
    ) {
      return 'forbidden';
    }
    if (isXmlError) {
      return 'error';
    }
    return 'content';
  };

  return (
    <Container>
      {modificationsStore.state.status === 'moving-token' &&
        businessObjects && (
          <ModificationInfoBanner
            text="Select the target element in the diagram"
            button={{
              onClick: () =>
                finishMovingToken(
                  affectedTokenCount,
                  visibleAffectedTokenCount,
                  businessObjects,
                  processInstance?.processDefinitionId,
                ),
              label: 'Discard',
            }}
          />
        )}
      <DiagramPanel>
        <DiagramShell status={getStatus()}>
          {processDefinitionData?.xml !== undefined &&
            businessObjects &&
            processInstance && (
              <Diagram
                xml={processDefinitionData?.xml}
                processDefinitionKey={processDefinitionKey}
                selectableElements={
                  isModificationModeEnabled
                    ? modifiableElements
                    : selectableElements
                }
                selectedElementIds={selectedElementIds}
                onRootChange={(rootElementId, getSelectionRootId) => {
                  if (!selectedElementId) {
                    return;
                  }

                  if (rootElementId !== getSelectionRootId(selectedElementId)) {
                    clearSelection();
                  }
                }}
                onElementSelection={(elementId, isMultiInstance) => {
                  if (modificationsStore.state.status === 'moving-token') {
                    const ancestorScopeType = getAncestorScopeType(
                      businessObjects,
                      sourceElementIdForMoveOperation ?? '',
                      elementId ?? '',
                      totalRunningInstancesByElement,
                    );

                    clearSelection();
                    finishMovingToken(
                      affectedTokenCount,
                      visibleAffectedTokenCount,
                      businessObjects,
                      processInstance?.processDefinitionId,
                      elementId,
                      ancestorScopeType,
                    );
                  } else {
                    if (modificationsStore.state.status !== 'adding-token') {
                      if (elementId !== undefined) {
                        selectElement({
                          elementId,
                          isMultiInstanceBody: isMultiInstance,
                        });
                      } else {
                        clearSelection();
                      }
                    }
                  }
                }}
                overlaysData={
                  isModificationModeEnabled
                    ? [
                        ...(elementStateOverlays ?? []),
                        ...modificationBadgesPerElement.get(),
                        ...agentStatusOverlays,
                      ]
                    : [...(elementStateOverlays ?? []), ...agentStatusOverlays]
                }
                selectedElementOverlay={
                  isModificationModeEnabled && <ModificationDropdown />
                }
                highlightedSequenceFlows={highlightedSequenceFlows}
                highlightedElementIds={highlightedSequenceFlowIds}
                nonSelectableNodeTooltipText={
                  isModificationModeEnabled
                    ? 'Modification is not supported for this element.'
                    : undefined
                }
                hasOuterBorderOnSelection={
                  !isModificationModeEnabled ||
                  hasSelectedElementMultipleRunningInstances
                }
                customElementClasses={customElementClasses}
                onElementDoubleClick={(elementId) => {
                  const elementType = businessObjects?.[elementId]?.$type;
                  if (elementType) {
                    handleDrillDown(elementId, elementType);
                  }
                }}
              >
                {stateOverlays.map((overlay) => {
                  const payload = overlay.payload as {
                    elementState: ElementState | 'completedEndEvents';
                    count: number;
                  };

                  return (
                    <StateOverlay
                      key={`${overlay.elementId}-${payload.elementState}`}
                      state={payload.elementState}
                      count={payload.count}
                      container={overlay.container}
                      isFaded={hasPendingCancelOrMoveModification({
                        elementId: overlay.elementId,
                        elementInstanceKey: undefined,
                        modificationsByElement: modificationsByElement,
                      })}
                      title={
                        payload.elementState === 'completed'
                          ? 'Execution Count'
                          : undefined
                      }
                    />
                  );
                })}
                {modificationBadgeOverlays?.map((overlay) => {
                  const payload = overlay.payload as ModificationBadgePayload;

                  return (
                    <ModificationBadgeOverlay
                      key={overlay.elementId}
                      container={overlay.container}
                      newTokenCount={payload.newTokenCount}
                      cancelledTokenCount={payload.cancelledTokenCount}
                    />
                  );
                })}
                {agentOverlays.map((overlay) => {
                  const payload = overlay.payload as {label: string};
                  return (
                    <AgentStatusOverlay
                      key={overlay.elementId}
                      container={overlay.container}
                      label={payload.label}
                    />
                  );
                })}
              </Diagram>
            )}
        </DiagramShell>
      </DiagramPanel>
    </Container>
  );
});

export {TopPanel};
