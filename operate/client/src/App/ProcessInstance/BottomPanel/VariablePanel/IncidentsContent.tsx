/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {useIsRootNodeSelected, useRootNode} from 'modules/hooks/flowNodeSelection';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {useGetIncidentsByElementInstancePaginated} from 'modules/queries/incidents/useGetIncidentsByElementInstancePaginated';
import {useGetIncidentsByProcessInstancePaginated} from 'modules/queries/incidents/useGetIncidentsByProcessInstancePaginated';
import {useEnhancedIncidents, useIncidentsSort} from 'modules/hooks/incidents';
import {getIncidentsSearchFilter} from 'modules/utils/incidents';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {IncidentsList} from '../../IncidentsWrapper/IncidentsList';
import {IncidentsFilter} from '../../IncidentsWrapper/IncidentsFilter';
import {
  Content,
  FilterContainer,
  ViewAllIncidentsBar,
  ResolvedAccordionContainer,
} from './IncidentsContent.styled';
import {isInstanceRunning} from 'modules/utils/instance';
import {modificationsStore} from 'modules/stores/modifications';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';
import {Accordion, AccordionItem, Button} from '@carbon/react';
import {Layer} from '@carbon/react';
import type {InfiniteData, UseInfiniteQueryResult} from '@tanstack/react-query';
import type {QueryIncidentsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';

function computeDisplayState(
  result: UseInfiniteQueryResult<InfiniteData<QueryIncidentsResponseBody>>,
  totalItems: number,
): React.ComponentProps<typeof IncidentsList>['state'] {
  switch (true) {
    case result.status === 'pending':
      return 'skeleton';
    case result.isFetching &&
      !result.isRefetching &&
      !result.isFetchingPreviousPage &&
      !result.isFetchingNextPage:
      return 'loading';
    case result.status === 'error':
      return 'error';
    case result.status === 'success' && totalItems === 0:
      return 'empty';
    default:
      return 'content';
  }
}

function mapQueryResult(
  result: UseInfiniteQueryResult<InfiniteData<QueryIncidentsResponseBody>>,
) {
  const incidents = result.data?.pages.flatMap((p) => p.items) ?? [];
  const totalIncidentsCount = result.data?.pages[0]?.page.totalItems ?? 0;
  return {
    incidents,
    displayState: computeDisplayState(result, totalIncidentsCount),
  };
}

const IncidentsContent: React.FC = observer(() => {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {data: processInstance} = useProcessInstance();
  const isRootNodeSelected = useIsRootNodeSelected();
  const selection = flowNodeSelectionStore.state.selection;
  const elementInstanceKey = selection?.flowNodeInstanceId;

  const enablePeriodicRefetch =
    processInstance &&
    isInstanceRunning(processInstance) &&
    !modificationsStore.isModificationModeEnabled;

  const sort = useIncidentsSort();
  const flowNodeId = selection?.flowNodeId;
  const isElementInstanceSelected =
    elementInstanceKey &&
    elementInstanceKey !== processInstance?.processInstanceKey;
  const isFlowNodeScoped = flowNodeId && !isRootNodeSelected;

  // For root node: show all process incidents with filters
  if (isRootNodeSelected && processInstance) {
    const filter = getIncidentsSearchFilter(
      incidentsPanelStore.state.selectedErrorTypes,
      incidentsPanelStore.state.selectedElementId ?? undefined,
    );

    const result = useGetIncidentsByProcessInstancePaginated(
      processInstance.processInstanceKey,
      {
        enabled: true,
        enablePeriodicRefetch,
        payload: {sort, filter},
      },
    );

    const {incidents, displayState} = mapQueryResult(result);
    const enhancedIncidents = useEnhancedIncidents(incidents);

    return (
      <Content>
        <FilterContainer>
          <IncidentsFilter />
        </FilterContainer>
        <IncidentsList
          state={displayState}
          processInstanceKey={processInstance.processInstanceKey}
          incidents={enhancedIncidents}
        />
        <ResolvedAccordionContainer>
          <Accordion>
            <AccordionItem title="Resolved incidents">
              <IncidentsList
                state={displayState}
                processInstanceKey={processInstance.processInstanceKey}
                incidents={enhancedIncidents}
              />
            </AccordionItem>
          </Accordion>
        </ResolvedAccordionContainer>
      </Content>
    );
  }

  // For selected flow node: show incidents filtered by flow node with filters
  if (isFlowNodeScoped && !isElementInstanceSelected && processInstance) {
    const filter = getIncidentsSearchFilter(
      incidentsPanelStore.state.selectedErrorTypes,
      flowNodeId,
    );

    const result = useGetIncidentsByProcessInstancePaginated(
      processInstance.processInstanceKey,
      {
        enabled: true,
        enablePeriodicRefetch,
        payload: {sort, filter},
      },
    );

    const {incidents, displayState} = mapQueryResult(result);
    const enhancedIncidents = useEnhancedIncidents(incidents);
    const rootNode = useRootNode();

    const handleViewAllIncidents = () => {
      selectFlowNode(rootNode, {
        processInstanceId: processInstance.processInstanceKey,
      });
    };

    return (
      <Content>
        <FilterContainer>
          <IncidentsFilter />
        </FilterContainer>
        <IncidentsList
          state={displayState}
          processInstanceKey={processInstance.processInstanceKey}
          incidents={enhancedIncidents}
        />
        <ResolvedAccordionContainer>
          <Accordion>
            <AccordionItem title="Resolved incidents">
              <IncidentsList
                state={displayState}
                processInstanceKey={processInstance.processInstanceKey}
                incidents={enhancedIncidents}
              />
            </AccordionItem>
          </Accordion>
        </ResolvedAccordionContainer>
        <ViewAllIncidentsBar>
          <Layer>
            <div />
            <div />
            <Button kind="ghost" size="md" onClick={handleViewAllIncidents}>
              View all incidents
            </Button>
          </Layer>
        </ViewAllIncidentsBar>
      </Content>
    );
  }

  // For selected element instance: show only that element's incidents without filters
  if (isElementInstanceSelected && processInstance) {
    const filter = getIncidentsSearchFilter(
      incidentsPanelStore.state.selectedErrorTypes,
    );

    const result = useGetIncidentsByElementInstancePaginated(
      elementInstanceKey,
      {
        enabled: true,
        enablePeriodicRefetch,
        payload: {sort, filter},
      },
    );

    const {incidents, displayState} = mapQueryResult(result);
    const enhancedIncidents = useEnhancedIncidents(incidents);
    const rootNode = useRootNode();

    const handleViewAllIncidents = () => {
      selectFlowNode(rootNode, {
        processInstanceId: processInstance.processInstanceKey,
      });
    };

    return (
      <Content>
        <IncidentsList
          state={displayState}
          processInstanceKey={processInstance.processInstanceKey}
          incidents={enhancedIncidents}
        />
        <ResolvedAccordionContainer>
          <Accordion>
            <AccordionItem title="Resolved incidents">
              <IncidentsList
                state={displayState}
                processInstanceKey={processInstance.processInstanceKey}
                incidents={enhancedIncidents}
              />
            </AccordionItem>
          </Accordion>
        </ResolvedAccordionContainer>
        {isFlowNodeScoped && (
          <ViewAllIncidentsBar>
            <Layer>
              <div />
              <div />
              <Button kind="ghost" size="md" onClick={handleViewAllIncidents}>
                View all incidents
              </Button>
            </Layer>
          </ViewAllIncidentsBar>
        )}
      </Content>
    );
  }

  return null;
});

export {IncidentsContent};
