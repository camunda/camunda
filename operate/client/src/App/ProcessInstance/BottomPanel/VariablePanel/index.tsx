/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState, useMemo, useEffect, useRef} from 'react';
import {observer} from 'mobx-react';

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {TabView} from 'modules/components/TabView';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {InputOutputMappings} from './InputOutputMappings';
import {VariablesContent} from './VariablesContent';
import {DetailsContent} from './DetailsContent';
import {IncidentsContent} from './IncidentsContent';
import {Listeners, type ListenerTypeFilter} from './Listeners';
import {OperationsLog} from './OperationsLog';
import {WarningFilled} from './styled';
import {useJobs} from 'modules/queries/jobs/useJobs';
import {useIsRootNodeSelected} from 'modules/hooks/flowNodeSelection';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessInstanceIncidentsCount} from 'modules/queries/incidents/useProcessInstanceIncidentsCount';
import {useGetIncidentsByElementInstance} from 'modules/queries/incidents/useGetIncidentsByElementInstance';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {useElementSelectionInstanceKey} from 'modules/hooks/useElementSelectionInstanceKey';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {IS_ELEMENT_SELECTION_V2} from 'modules/feature-flags';
import {useHasAgentContext} from 'modules/queries/agentContext/useHasAgentContext';
import {isRunning} from 'modules/utils/instance';
import {AgentContextTab} from './AgentContextTab';
import {fireAgentTabConfettiOnce} from 'modules/agentContext/confetti/agentTabConfetti';

const tabIds = {
  incidents: 'incidents',
  details: 'details',
  variables: 'variables',
  inputMappings: 'input-mappings',
  outputMappings: 'output-mappings',
  listeners: 'listeners',
  operationsLog: 'operations-log',
  agentContext: 'agent-context',
} as const;

type TabId = (typeof tabIds)[keyof typeof tabIds];

type Props = {
  setListenerTabVisibility: React.Dispatch<React.SetStateAction<boolean>>;
};

const VariablePanel: React.FC<Props> = observer(function VariablePanel({
  setListenerTabVisibility,
}) {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const isRootNodeSelected = useIsRootNodeSelected();
  const {data: processInstance} = useProcessInstance();
  let {
    hasSelection,
    selectedElementId,
    selectedElementInstanceKey,
    resolvedElementInstance,
  } = useProcessInstanceElementSelection();
  let resolvedElementInstanceKey = useElementSelectionInstanceKey();

  // TODO: Remove these assignments to remove the feature flag from the component
  hasSelection = IS_ELEMENT_SELECTION_V2
    ? hasSelection
    : // eslint-disable-next-line react-hooks/rules-of-hooks
      !useIsRootNodeSelected();
  selectedElementId = IS_ELEMENT_SELECTION_V2
    ? selectedElementId
    : (flowNodeSelectionStore.state.selection?.flowNodeId ?? null);
  resolvedElementInstanceKey = IS_ELEMENT_SELECTION_V2
    ? resolvedElementInstanceKey
    : (flowNodeSelectionStore.state.selection?.flowNodeInstanceId ?? null);

  const [listenerTypeFilter, setListenerTypeFilter] =
    useState<ListenerTypeFilter>();

  const selectedScopeKey =
    resolvedElementInstance?.elementInstanceKey ??
    selectedElementInstanceKey ??
    resolvedElementInstanceKey ??
    null;

  const selectionKey = `${processInstanceId}:${selectedScopeKey ?? ''}:$$${
    selectedElementId ?? ''
  }`;

  const isAdHocSelectionByType =
    resolvedElementInstance?.type === 'AD_HOC_SUB_PROCESS' ||
    resolvedElementInstance?.type === 'AD_HOC_SUB_PROCESS_INNER_INSTANCE';

  // Fallback: if the instance isn't resolved yet, still allow the AI Agent tab for known ad-hoc ids.
  // NOTE: adjust this mapping once the final BPMN element ids are confirmed.
  const isAdHocSelectionByElementId =
    selectedElementId === 'AI_Agent' || selectedElementId === 'AI Agent';

  const isAdHocSelection =
    isAdHocSelectionByType || isAdHocSelectionByElementId;

  const isSelectedScopeRunning = resolvedElementInstance
    ? isRunning(resolvedElementInstance)
    : false;

  // If we don't have a scopeKey yet (e.g. element instance resolution still pending),
  // fall back to checking for agentContext on the whole process instance.
  const shouldUseProcessWideGate = selectedScopeKey === null;

  const [adHocReloadToken, setAdHocReloadToken] = useState(0);
  const lastAdHocSelectionKeyRef = useRef<string | null>(null);

  useEffect(() => {
    if (!isAdHocSelection) {
      lastAdHocSelectionKeyRef.current = null;
      return;
    }

    // Bump the reload token whenever the AHSP selection changes (or is re-established).
    // This ensures agentContext is reloaded each time the user selects the AHSP.
    if (lastAdHocSelectionKeyRef.current !== selectionKey) {
      lastAdHocSelectionKeyRef.current = selectionKey;
      setAdHocReloadToken((v) => v + 1);
    }
  }, [isAdHocSelection, selectionKey]);

  const {data: agentContextGate} = useHasAgentContext({
    processInstanceKey: processInstanceId,
    scopeKey: shouldUseProcessWideGate ? null : selectedScopeKey,
    enabled: Boolean(isAdHocSelection),
    reloadToken: adHocReloadToken,
  });

  const hasAgentContext = Boolean(agentContextGate?.hasAgentContext);

  const lastSelectionKeyRef = useRef<string | null>(null);
  const [stickyHasAgentContext, setStickyHasAgentContext] = useState(false);

  useEffect(() => {
    // Reset stickiness when selection changes
    if (lastSelectionKeyRef.current !== selectionKey) {
      lastSelectionKeyRef.current = selectionKey;
      setStickyHasAgentContext(false);

      // Also reset the visibility transition tracker so confetti can fire for the new selection.
      prevShouldShowAgentTabRef.current = false;
    }
  }, [selectionKey]);

  useEffect(() => {
    if (hasAgentContext) {
      setStickyHasAgentContext(true);
    }
  }, [hasAgentContext]);

  const shouldShowAgentTab = Boolean(
    isAdHocSelection && (hasAgentContext || stickyHasAgentContext),
  );

  // Debugging: this is intentionally verbose to help validate when/why the tab appears.
  console.info('[AI Agent tab] render', {
    processInstanceKey: processInstanceId,
    selectedScopeKey,
    selectedElementId,
    selectedElementInstanceKey,
    resolvedElementInstanceKey,
    elementType: resolvedElementInstance?.type,
    elementState: resolvedElementInstance?.state,
    isAdHocSelection,
    isAdHocSelectionByType,
    isAdHocSelectionByElementId,
    shouldUseProcessWideGate,
    hasAgentContext,
    stickyHasAgentContext,
    shouldShowAgentTab,
  });

  useEffect(() => {
    console.info('[AI Agent tab] show condition', {
      processInstanceKey: processInstanceId,
      selectedScopeKey,
      elementType: resolvedElementInstance?.type,
      elementState: resolvedElementInstance?.state,
      isAdHocSelection,
      hasAgentContext,
      isSelectedScopeRunning,
      shouldShowAgentTab,
    });
  }, [
    processInstanceId,
    selectedScopeKey,
    resolvedElementInstance?.type,
    resolvedElementInstance?.state,
    isAdHocSelection,
    hasAgentContext,
    isSelectedScopeRunning,
    shouldShowAgentTab,
  ]);

  const initialSelectedTab = useMemo<TabId>(() => {
    return shouldShowAgentTab ? tabIds.agentContext : tabIds.variables;
  }, [shouldShowAgentTab]);

  const [selectedTab, setSelectedTab] = useState<TabId>(initialSelectedTab);

  const prevShouldShowAgentTabRef = useRef(false);

  useEffect(() => {
    const justBecameVisible =
      shouldShowAgentTab && !prevShouldShowAgentTabRef.current;
    prevShouldShowAgentTabRef.current = shouldShowAgentTab;

    if (shouldShowAgentTab) {
      console.info('[AI Agent tab] auto-selecting agent tab for selection', {
        selectedElementId,
        resolvedElementInstanceKey,
        selectedScopeKey,
      });
      setSelectedTab(tabIds.agentContext);

      if (justBecameVisible) {
        // Confetti: when the tab first appears.
        requestAnimationFrame(() => {
          const el = document.querySelector(
            '[data-testid="ai-agent-tab-button"]',
          ) as HTMLElement | null;
          fireAgentTabConfettiOnce(el);
        });
      }

      return;
    }

    setSelectedTab(tabIds.variables);
  }, [
    shouldShowAgentTab,
    selectedElementId,
    resolvedElementInstanceKey,
    selectedScopeKey,
    setSelectedTab,
  ]);

  const {data: statistics} = useFlownodeInstancesStatistics();

  // Get incident counts
  const processIncidentsCount = useProcessInstanceIncidentsCount(
    processInstanceId,
    {
      enabled: isRootNodeSelected && !!processInstance?.hasIncident,
    },
  );

  const {data: elementIncidentsData} = useGetIncidentsByElementInstance(
    resolvedElementInstanceKey ?? '',
    {
      enabled: !isRootNodeSelected && !!resolvedElementInstanceKey,
      select: (data) => data.page.totalItems,
    },
  );

  const elementIncidentsCount = elementIncidentsData ?? 0;

  // Get flow node incidents count from statistics
  const flowNodeIncidentsFromStats =
    !isRootNodeSelected && selectedElementId
      ? (statistics?.items.find((item) => item.elementId === selectedElementId)
          ?.incidents ?? 0)
      : 0;

  // Use element incidents count if available, otherwise use statistics
  const elementIncidentsCountFinal =
    elementIncidentsCount > 0
      ? elementIncidentsCount
      : flowNodeIncidentsFromStats;

  // Check if we should show incidents tab
  const shouldShowIncidentsTab =
    (isRootNodeSelected && processIncidentsCount > 0) ||
    (!isRootNodeSelected && elementIncidentsCountFinal > 0);

  const incidentsTabLabel = shouldShowIncidentsTab
    ? `Incidents (${isRootNodeSelected ? processIncidentsCount : elementIncidentsCountFinal})`
    : 'Incidents';

  const shouldFetchListeners = resolvedElementInstanceKey || selectedElementId;
  const {
    data: jobs,
    fetchNextPage,
    fetchPreviousPage,
    hasNextPage,
    hasPreviousPage,
  } = useJobs({
    payload: {
      filter: {
        processInstanceKey: processInstanceId,
        elementId: selectedElementId ?? undefined,
        elementInstanceKey: resolvedElementInstanceKey ?? undefined,
        kind: listenerTypeFilter,
      },
    },
    disabled: !shouldFetchListeners,
    select: (data) => data.pages?.flatMap((page) => page.items),
  });

  const hasFailedListeners = jobs?.some(({state}) => state === 'FAILED');

  return (
    <TabView
      key={`tabview-${selectedElementId}-${resolvedElementInstanceKey}-${selectedScopeKey}`}
      onTabChange={(tabId) => setSelectedTab(tabId)}
      tabs={[
        ...(shouldShowAgentTab
          ? [
              {
                id: tabIds.agentContext,
                label: 'AI Agent',
                content: (
                  <AgentContextTab
                    isVisible={selectedTab === tabIds.agentContext}
                    processInstanceKey={processInstanceId}
                    scopeKey={selectedScopeKey}
                    isRunning={isSelectedScopeRunning}
                    reloadToken={adHocReloadToken}
                  />
                ),
                removePadding: true,
                onClick: () => {
                  setListenerTabVisibility(false);
                },
                testId: 'ai-agent-tab-button',
              },
            ]
          : []),
        ...(shouldShowIncidentsTab
          ? [
              {
                id: tabIds.incidents,
                label: incidentsTabLabel,
                content: <IncidentsContent />,
                removePadding: true,
                onClick: () => {
                  setListenerTabVisibility(false);
                },
              },
            ]
          : []),
        ...(isRootNodeSelected
          ? []
          : [
              {
                id: tabIds.details,
                label: 'Details',
                content: <DetailsContent />,
                removePadding: true,
                onClick: () => {
                  setListenerTabVisibility(false);
                },
              },
            ]),
        {
          id: tabIds.variables,
          label: 'Variables',
          content: <VariablesContent />,
          removePadding: true,
          onClick: () => {
            setListenerTabVisibility(false);
          },
        },
        ...(!hasSelection || !selectedElementId
          ? []
          : [
              {
                id: tabIds.inputMappings,
                label: 'Input Mappings',
                content: (
                  <InputOutputMappings
                    type="Input"
                    elementId={selectedElementId}
                  />
                ),
                onClick: () => {
                  setListenerTabVisibility(false);
                },
              },
              {
                id: tabIds.outputMappings,
                label: 'Output Mappings',
                content: (
                  <InputOutputMappings
                    type="Output"
                    elementId={selectedElementId}
                  />
                ),
                onClick: () => {
                  setListenerTabVisibility(false);
                },
              },
            ]),
        {
          id: tabIds.listeners,
          testId: 'listeners-tab-button',
          ...(hasFailedListeners && {
            labelIcon: <WarningFilled />,
          }),
          label: 'Listeners',
          content: (
            <Listeners
              jobs={jobs}
              setListenerTypeFilter={setListenerTypeFilter}
              fetchNextPage={fetchNextPage}
              fetchPreviousPage={fetchPreviousPage}
              hasNextPage={hasNextPage}
              hasPreviousPage={hasPreviousPage}
            />
          ),
          removePadding: true,
          onClick: () => {
            setListenerTabVisibility(true);
          },
        },
        {
          id: tabIds.operationsLog,
          label: 'Operations Log',
          content: (
            <OperationsLog isVisible={selectedTab === tabIds.operationsLog} />
          ),
          removePadding: true,
          onClick: () => {
            setListenerTabVisibility(false);
          },
        },
      ]}
    />
  );
});

export {VariablePanel};
