/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect, useMemo, useState} from 'react';
import {observer} from 'mobx-react';

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {TabView} from 'modules/components/TabView';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {InputOutputMappings} from './InputOutputMappings';
import {VariablesContent} from './VariablesContent';
import {Listeners} from './Listeners/v2';
import {WarningFilled} from './styled';
import {useJobs, type JobsFilter} from 'modules/queries/jobs/useJobs';
import {useIsRootNodeSelected} from 'modules/hooks/flowNodeSelection';

// TODO: Remove when listeners tab is fully migrated to v2
import {processInstanceListenersStore} from 'modules/stores/processInstanceListeners';
import {Listeners as ListenersLegacy} from './Listeners';
import {IS_LISTENERS_TAB_V2} from 'modules/feature-flags';
import type {ListenerEntity} from 'modules/types/operate';

type Props = {
  setListenerTabVisibility: React.Dispatch<React.SetStateAction<boolean>>;
};

const VariablePanel: React.FC<Props> = observer(function VariablePanel({
  setListenerTabVisibility,
}) {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const isRootNodeSelected = useIsRootNodeSelected();

  const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
  const flowNodeInstanceId =
    flowNodeSelectionStore.state.selection?.flowNodeInstanceId;

  const [listenerTypeFilter, setListenerTypeFilter] =
    useState<ListenerEntity['listenerType']>();

  // TODO: remove when listeners tab is fully migrated to v2
  const {listenersFailureCount, fetchListeners, reset} =
    processInstanceListenersStore;

  const jobsFilter = useMemo<JobsFilter | undefined>(() => {
    if (!flowNodeInstanceId && !flowNodeId) {
      // We cannot meaningfully filter for a flow-node if none is selected.
      // Therefore, no filter is defined to not trigger a job search.
      return;
    }

    return {
      processInstanceKey: processInstanceId,
      elementId: flowNodeId,
      elementInstanceKey: flowNodeInstanceId,
      kind: listenerTypeFilter,
    };
  }, [processInstanceId, flowNodeId, flowNodeInstanceId, listenerTypeFilter]);

  const {
    data: jobs,
    fetchNextPage,
    fetchPreviousPage,
    hasNextPage,
    hasPreviousPage,
  } = useJobs({
    payload: {filter: jobsFilter},
    disabled: !IS_LISTENERS_TAB_V2 || !jobsFilter,
    select: (data) => data.pages?.flatMap((page) => page.items),
  });

  const hasFailedListeners = jobs?.some(({state}) => state === 'FAILED');

  // TODO: remove when listeners tab is fully migrated to v2
  useEffect(() => {
    if (IS_LISTENERS_TAB_V2) {
      return;
    }
    reset();
  }, [flowNodeId, flowNodeInstanceId, reset]);
  useEffect(() => {
    if (IS_LISTENERS_TAB_V2) {
      return;
    }
    if (flowNodeInstanceId) {
      fetchListeners({
        fetchType: 'initial',
        processInstanceId: processInstanceId,
        payload: {
          flowNodeInstanceId,
          ...(listenerTypeFilter && {listenerTypeFilter}),
        },
      });
    } else if (flowNodeId) {
      fetchListeners({
        fetchType: 'initial',
        processInstanceId: processInstanceId,
        payload: {
          flowNodeId,
          ...(listenerTypeFilter && {listenerTypeFilter}),
        },
      });
    }
  }, [
    fetchListeners,
    processInstanceId,
    flowNodeId,
    flowNodeInstanceId,
    listenerTypeFilter,
  ]);

  return (
    <TabView
      tabs={[
        {
          id: 'variables',
          label: 'Variables',
          content: <VariablesContent />,
          removePadding: true,
          onClick: () => {
            setListenerTabVisibility(false);
          },
        },
        ...(isRootNodeSelected
          ? []
          : [
              {
                id: 'input-mappings',
                label: 'Input Mappings',
                content: <InputOutputMappings type="Input" />,
                onClick: () => {
                  setListenerTabVisibility(false);
                },
              },
              {
                id: 'output-mappings',
                label: 'Output Mappings',
                content: <InputOutputMappings type="Output" />,
                onClick: () => {
                  setListenerTabVisibility(false);
                },
              },
            ]),
        ...(IS_LISTENERS_TAB_V2
          ? [
              {
                id: 'listeners',
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
            ]
          : [
              {
                id: 'listeners',
                testId: 'listeners-tab-button',
                ...(listenersFailureCount > 0 && {
                  labelIcon: <WarningFilled />,
                }),
                label: 'Listeners',
                content: <ListenersLegacy />,
                removePadding: true,
                onClick: () => {
                  setListenerTabVisibility(true);
                },
              },
            ]),
      ]}
      key={`tabview-${flowNodeId}-${flowNodeInstanceId}`}
    />
  );
});

export {VariablePanel};
