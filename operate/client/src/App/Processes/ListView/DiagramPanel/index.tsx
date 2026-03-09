/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useSearchParams} from 'react-router-dom';
import {observer} from 'mobx-react';
import {Section} from './styled';
import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {notificationsStore} from 'modules/stores/notifications';
import {StateOverlay} from 'modules/components/StateOverlay';
import {batchModificationStore} from 'modules/stores/batchModification';
import {isMoveModificationTarget} from 'modules/bpmn-js/utils/isMoveModificationTarget';
import {ModificationBadgeOverlay} from 'App/ProcessInstance/TopPanel/ModificationBadgeOverlay';
import {BatchModificationNotification} from './BatchModificationNotification';
import {DiagramHeader} from './DiagramHeader';
import {useProcessInstancesOverlayData} from 'modules/queries/processInstancesStatistics/useOverlayData';
import {useBatchModificationOverlayData} from 'modules/queries/processInstancesStatistics/useBatchModificationOverlayData';
import {useListViewXml} from 'modules/queries/processDefinitions/useListViewXml';
import {
  getElement,
  getSubprocessOverlayFromIncidentElements,
} from 'modules/utils/elements';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {parseProcessInstancesFilter} from 'modules/utils/filter/v2/processInstancesSearch';
import {
  getProcessDefinitionName,
  useProcessDefinitionSelection,
} from 'modules/hooks/processDefinitions';
import {getSelectedProcessInstancesFilter} from 'modules/queries/processInstancesStatistics/filters';
import {useProcessInstanceStatisticsFilters} from 'modules/hooks/useProcessInstanceStatisticsFilters';
import {
  isStatisticsPayload,
  isModificationBadgePayload,
} from 'modules/bpmn-js/overlayTypes';

const DiagramPanel: React.FC = observer(() => {
  const [searchParams, setSearchParams] = useSearchParams();
  const {elementId} = parseProcessInstancesFilter(searchParams);

  const {
    data: definitionSelection = {kind: 'no-match'},
    status: definitionSelectionStatus,
    isLoading: isDefinitionSelectionLoading,
    isEnabled: isDefinitionSelectionEnabled,
    isError: isDefinitionSelectionError,
  } = useProcessDefinitionSelection();
  const selectedDefinitionKey =
    definitionSelection.kind === 'single-version'
      ? definitionSelection.definition.processDefinitionKey
      : undefined;
  const selectedDefinitionName =
    definitionSelection.kind !== 'no-match'
      ? getProcessDefinitionName(definitionSelection.definition)
      : 'Process';

  useEffect(() => {
    if (
      definitionSelectionStatus === 'success' &&
      definitionSelection.kind === 'no-match'
    ) {
      setSearchParams((p) => {
        p.delete('processDefinitionId');
        p.delete('processDefinitionVersion');
        return p;
      });
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Process could not be found',
        isDismissable: true,
      });
    }
  }, [definitionSelection, definitionSelectionStatus, setSearchParams]);

  const statisticsOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type.startsWith('statistics-'),
  );

  const batchModificationBadgeOverlays =
    diagramOverlaysStore.state.overlays.filter(
      ({type}) => type === 'batchModificationsBadge',
    );

  const {
    data: processDefinitionXML,
    isFetching: isXmlFetching,
    isError: isXmlError,
  } = useListViewXml({
    processDefinitionKey: selectedDefinitionKey,
  });
  const selectableIds = processDefinitionXML?.selectableElements.map(
    (element) => element.id,
  );

  const {data: businessObjects} = useBusinessObjects();

  const baseFilters = useProcessInstanceStatisticsFilters();
  const processInstanceKeyFilter = getSelectedProcessInstancesFilter();

  const {data: processInstanceOverlayData} = useProcessInstancesOverlayData(
    baseFilters,
    selectedDefinitionKey,
  );

  const {selectedTargetElementId} = batchModificationStore.state;
  const {data: batchOverlayData} = useBatchModificationOverlayData(
    {
      ...baseFilters,
      filter: {
        ...baseFilters.filter,
        processInstanceKey: processInstanceKeyFilter,
      },
    },
    {
      sourceElementId: elementId,
      targetElementId: selectedTargetElementId ?? undefined,
    },
    selectedDefinitionKey,
    batchModificationStore.state.isEnabled,
  );

  const elementIdsWithIncidents = processInstanceOverlayData
    ?.filter(({type}) => type === 'statistics-incidents')
    ?.map((overlay) => overlay.elementId);

  const selectableElementsWithIncidents = elementIdsWithIncidents?.map(
    (elementId) => businessObjects?.[elementId],
  );

  const subprocessOverlays = getSubprocessOverlayFromIncidentElements(
    selectableElementsWithIncidents,
    'statistics-incidents',
  );

  const getStatus = () => {
    switch (true) {
      case isXmlFetching || isDefinitionSelectionLoading:
        return 'loading';
      case isXmlError || isDefinitionSelectionError:
        return 'error';
      case !isDefinitionSelectionEnabled ||
        definitionSelection.kind !== 'single-version':
        return 'empty';
      default:
        return 'content';
    }
  };

  const getSelectedElementIds = () => {
    if (!batchModificationStore.state.isEnabled) {
      return elementId ? [elementId] : undefined;
    }

    const ids: string[] = [];
    if (elementId) {
      ids.push(elementId);
    }
    if (selectedTargetElementId) {
      ids.push(selectedTargetElementId);
    }
    return ids;
  };

  const getSelectableElements = () => {
    if (!batchModificationStore.state.isEnabled) {
      return selectableIds;
    }

    return selectableIds?.filter((selectedElementId) => {
      if (selectedElementId === elementId) {
        return false;
      }
      if (selectedElementId === undefined) {
        return false;
      }

      const element = getElement({
        businessObjects: processDefinitionXML?.diagramModel.elementsById,
        elementId: selectedElementId,
      });

      return isMoveModificationTarget(element);
    });
  };

  const getOverlaysData = () => {
    const baseOverlays = processInstanceOverlayData ?? [];

    if (batchModificationStore.state.isEnabled) {
      return [...baseOverlays, ...(batchOverlayData ?? [])];
    }

    return [...baseOverlays, ...(subprocessOverlays ?? [])];
  };

  const handleElementSelection = (
    selectedElementId: string | null | undefined,
  ) => {
    if (batchModificationStore.state.isEnabled) {
      return batchModificationStore.selectTargetElement(
        selectedElementId ?? null,
      );
    }

    if (selectedElementId === null || selectedElementId === undefined) {
      setSearchParams((p) => {
        p.delete('elementId');
        return p;
      });
      return;
    }

    setSearchParams((p) => {
      p.set('elementId', selectedElementId);
      return p;
    });
  };

  return (
    <Section aria-label="Diagram Panel">
      <DiagramHeader processDefinitionSelection={definitionSelection} />
      <DiagramShell
        status={getStatus()}
        emptyMessage={
          definitionSelection.kind === 'all-versions'
            ? {
                message: `There is more than one Version selected for Process "${selectedDefinitionName}"`,
                additionalInfo: 'To see a Diagram, select a single Version',
              }
            : {
                message: 'There is no Process selected',
                additionalInfo:
                  'To see a Diagram, select a Process in the Filters panel',
              }
        }
      >
        {processDefinitionXML?.xml !== undefined && (
          <Diagram
            xml={processDefinitionXML.xml}
            processDefinitionKey={selectedDefinitionKey}
            selectedElementIds={getSelectedElementIds()}
            onElementSelection={handleElementSelection}
            overlaysData={getOverlaysData()}
            selectableElements={getSelectableElements()}
          >
            {statisticsOverlays?.map((overlay) => {
              if (!isStatisticsPayload(overlay.payload)) {
                return null;
              }
              return (
                <StateOverlay
                  testId={`state-overlay-${overlay.elementId}-${overlay.payload.elementState}`}
                  key={`${overlay.elementId}-${overlay.payload.elementState}`}
                  state={overlay.payload.elementState}
                  count={overlay.payload.count}
                  container={overlay.container}
                />
              );
            })}
            {batchModificationBadgeOverlays?.map((overlay) => {
              if (!isModificationBadgePayload(overlay.payload)) {
                return null;
              }
              return (
                <ModificationBadgeOverlay
                  key={overlay.elementId}
                  container={overlay.container}
                  newTokenCount={overlay.payload.newTokenCount ?? 0}
                  cancelledTokenCount={overlay.payload.cancelledTokenCount ?? 0}
                />
              );
            })}
          </Diagram>
        )}
      </DiagramShell>
      {batchModificationStore.state.isEnabled && (
        <BatchModificationNotification
          sourceElementId={elementId}
          targetElementId={selectedTargetElementId || undefined}
          onUndoClick={() => batchModificationStore.selectTargetElement(null)}
        />
      )}
    </Section>
  );
});

export {DiagramPanel};
