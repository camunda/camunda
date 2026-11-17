/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useMemo} from 'react';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from '../useProcessInstancePageParams';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {tracking} from 'modules/tracking';
import {modificationsStore} from 'modules/stores/modifications';
import {Container, DiagramPanel} from './styled';
import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {ModificationInfoBanner} from './ModificationInfoBanner';
import {ModificationDropdown} from './ModificationDropdown';
import {DiagramOverlays} from './DiagramOverlays';
import {useDiagramOverlaysData} from './DiagramOverlays/useDiagramOverlaysData';
import {useSelectableElements} from 'modules/queries/elementInstancesStatistics/useSelectableElements';
import {useExecutedElements} from 'modules/queries/elementInstancesStatistics/useExecutedElements';
import {useModifiableElements} from 'modules/hooks/processInstanceDetailsDiagram';
import {
  useTotalRunningInstancesByElement,
  useTotalRunningInstancesForElement,
  useTotalRunningInstancesVisibleForElement,
} from 'modules/queries/elementInstancesStatistics/useTotalRunningInstancesForElement';
import {finishMovingToken} from 'modules/utils/modifications';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {isCompensationAssociation} from 'modules/bpmn-js/utils/isCompensationAssociation';
import {useProcessSequenceFlows} from 'modules/queries/sequenceFlows/useProcessSequenceFlows';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {isRequestError} from 'modules/request';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useDrillDownNavigation} from 'modules/hooks/useDrilldownNavigation';
import {getAncestorScopeType} from 'modules/utils/processInstanceDetailsDiagram';
import {isMultiInstance as isMultiInstanceElement} from 'modules/bpmn-js/utils/isMultiInstance';

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
  const affectedTokenCount = sourceElementInstanceKeyForMoveOperation
    ? 1
    : totalMoveOperationRunningInstances || 1;
  const visibleAffectedTokenCount = sourceElementInstanceKeyForMoveOperation
    ? 1
    : totalMoveOperationRunningInstancesVisible || 1;

  const {data: processedSequenceFlowsFromHook} =
    useProcessSequenceFlows(processInstanceId);
  const processDefinitionKey = useProcessDefinitionKeyContext();

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

  const modifiableElements = useModifiableElements();

  const {isModificationModeEnabled} = modificationsStore;

  const overlaysData = useDiagramOverlaysData(isModificationModeEnabled);

  const {handleDrillDown, pendingDrillDownElementId} =
    useDrillDownNavigation(processInstanceId);

  const customElementClasses = useMemo<
    [elementId: string, className: string][]
  >(() => {
    if (isModificationModeEnabled || !businessObjects) {
      return [];
    }

    // customElementClasses isn't just styling: BpmnJS only invokes
    // onElementDoubleClick for elements listed here (see
    // #handleElementDoubleClick), so this array gates drill-down, not just
    // its cosmetic affordance. Every call activity/business rule task is
    // included regardless of instance state - a completed one still has a
    // called instance worth navigating to, and one that never ran resolves
    // to nothing and silently no-ops in useDrillDownNavigation.
    const DRILLDOWN_TYPES = ['bpmn:CallActivity', 'bpmn:BusinessRuleTask'];
    const drilldownClasses: [string, string][] = Object.entries(businessObjects)
      .filter(([, bo]) => DRILLDOWN_TYPES.includes(bo.$type))
      .map(([elementId]) => [elementId, 'op-drilldown'] as const);

    if (pendingDrillDownElementId !== null) {
      drilldownClasses.push([
        pendingDrillDownElementId,
        'op-drilldown-loading',
      ]);
    }

    return drilldownClasses;
  }, [businessObjects, isModificationModeEnabled, pendingDrillDownElementId]);

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

  const handleElementSelection = useCallback(
    (
      elementId?: string,
      isMultiInstance?: boolean,
      clickedElementId?: string,
    ) => {
      if (
        modificationsStore.state.status === 'moving-token' &&
        businessObjects
      ) {
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
        return;
      }

      if (modificationsStore.state.status === 'adding-token') {
        return;
      }

      if (elementId !== undefined) {
        selectElement({
          elementId,
          isMultiInstanceBody: isMultiInstance,
        });
        return;
      }

      if (
        selectedElementId !== null &&
        clickedElementId !== undefined &&
        selectedElementIds?.includes(clickedElementId)
      ) {
        if (isModificationModeEnabled) {
          clearSelection();
          return;
        }

        selectElement({
          elementId: clickedElementId,
          isMultiInstanceBody: isMultiInstanceElement(
            businessObjects?.[clickedElementId],
          ),
        });
        return;
      }

      clearSelection();
    },
    [
      affectedTokenCount,
      businessObjects,
      clearSelection,
      isModificationModeEnabled,
      processInstance?.processDefinitionId,
      selectedElementId,
      selectedElementIds,
      selectElement,
      sourceElementIdForMoveOperation,
      totalRunningInstancesByElement,
      visibleAffectedTokenCount,
    ],
  );

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
                onElementSelection={handleElementSelection}
                overlaysData={overlaysData}
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
                <DiagramOverlays />
              </Diagram>
            )}
        </DiagramShell>
      </DiagramPanel>
    </Container>
  );
});

export {TopPanel};
