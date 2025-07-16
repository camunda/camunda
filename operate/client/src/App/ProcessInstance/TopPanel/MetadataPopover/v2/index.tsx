/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Divider, Popover, Content} from '../styled';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {flip, offset} from '@floating-ui/react-dom';
import {Header} from '../Header';
import {Stack} from '@carbon/react';
import {Incident} from '../Incident';
import {MultiIncidents} from '../MultiIncidents';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useElementInstancesSearch} from 'modules/queries/elementInstances/useElementInstancesSearch';
import {useElementInstance} from 'modules/queries/elementInstances/useElementInstance';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {useMemo} from 'react';
import {Details} from './Details';
import {createV2InstanceMetadata} from './types';

type Props = {
  selectedFlowNodeRef?: SVGGraphicsElement | null;
};

const MetadataPopover = observer(({selectedFlowNodeRef}: Props) => {
  const {data: processInstance} = useProcessInstance();
  const elementId = flowNodeSelectionStore.state.selection?.flowNodeId;
  const elementInstanceId =
    flowNodeSelectionStore.state.selection?.flowNodeInstanceId;
  const isMultiInstance =
    flowNodeSelectionStore.state.selection?.isMultiInstance;
  const {metaData} = flowNodeMetaDataStore.state;

  const {data: statistics} = useFlownodeInstancesStatistics();

  const instanceCount = useMemo(() => {
    if (!statistics?.items || !elementId) return null;
    const elementStats = statistics.items.find(
      (stat) => stat.elementId === elementId,
    );
    if (!elementStats) return 0;
    if (isMultiInstance) return 1;
    return (
      elementStats.active +
      elementStats.completed +
      elementStats.canceled +
      elementStats.incidents
    );
  }, [statistics, elementId, isMultiInstance]);

  const shouldFetchElementInstances =
    instanceCount === 1 &&
    !elementInstanceId &&
    !!processInstance?.processInstanceKey &&
    !!elementId;

  const {data: elementInstance, isLoading: isFetchingInstance} =
    useElementInstance(elementInstanceId ?? '', {
      enabled: !!elementInstanceId && !!elementId,
    });

  const {data: elementInstancesSearchResult, isLoading: isSearchingInstances} =
    useElementInstancesSearch(
      elementId,
      processInstance?.processInstanceKey,
      isMultiInstance,
      {
        enabled: shouldFetchElementInstances,
      },
    );

  const elementInstanceMetadata = useMemo(() => {
    if (elementInstanceId && elementInstance) {
      return elementInstance;
    }

    if (
      !elementInstanceId &&
      instanceCount === 1 &&
      elementInstancesSearchResult?.items?.length === 1
    ) {
      return elementInstancesSearchResult.items[0];
    }

    return null;
  }, [
    elementInstanceId,
    elementInstance,
    instanceCount,
    elementInstancesSearchResult,
  ]);

  if (
    elementId === undefined ||
    metaData === null ||
    (shouldFetchElementInstances && isSearchingInstances) ||
    (!!elementInstanceId && isFetchingInstance)
  ) {
    return null;
  }

  const {instanceMetadata, incident, incidentCount} = metaData;

  return (
    <Popover
      referenceElement={selectedFlowNodeRef}
      middlewareOptions={[
        offset(10),
        flip({
          fallbackPlacements: ['top', 'right', 'left'],
        }),
      ]}
      variant="arrow"
    >
      <Stack gap={3}>
        {instanceCount !== null && instanceCount > 1 && !elementInstanceId && (
          <>
            <Header
              title={`This Element instance triggered ${instanceCount} times`}
            />
            <Content>
              To view details for any of these, select one Instance in the
              Instance History.
            </Content>
          </>
        )}

        {elementInstanceMetadata && (
          <>
            <Details
              metaData={{
                ...metaData,
                instanceMetadata: createV2InstanceMetadata(
                  instanceMetadata,
                  elementInstanceMetadata,
                ),
              }}
              elementId={elementInstanceMetadata.elementId}
            />
            {incident !== null && (
              <>
                <Divider />
                <Incident
                  processInstanceId={processInstance?.processInstanceKey}
                  incident={incident}
                  onButtonClick={() => {
                    incidentsStore.clearSelection();
                    incidentsStore.toggleFlowNodeSelection(elementId);
                    incidentsStore.toggleErrorTypeSelection(
                      incident.errorType.id,
                    );
                    incidentsStore.setIncidentBarOpen(true);
                  }}
                />
              </>
            )}
          </>
        )}
        {incidentCount > 1 && (
          <>
            <Divider />
            <MultiIncidents
              count={incidentCount}
              onButtonClick={() => {
                incidentsStore.clearSelection();
                incidentsStore.toggleFlowNodeSelection(elementId);
                incidentsStore.setIncidentBarOpen(true);
              }}
            />
          </>
        )}
      </Stack>
    </Popover>
  );
});

export {MetadataPopover};
