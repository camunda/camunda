/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Popover, Content, Divider} from './styled';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';
import {flip, offset} from '@floating-ui/react-dom';
import {Header} from './Header';
import {Stack} from '@carbon/react';
import {MultiIncidents} from './Incidents/multiIncidents';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useElementInstancesSearch} from 'modules/queries/elementInstances/useElementInstancesSearch';
import {useElementInstance} from 'modules/queries/elementInstances/useElementInstance';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {useMemo} from 'react';
import {Details} from './Details';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {convertBpmnJsTypeToAPIType} from './convertBpmnJsTypeToAPIType';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {Incidents} from './Incidents';
import {useElementInstancesCount} from 'modules/hooks/useElementInstancesCount';

type Props = {
  selectedFlowNodeRef?: SVGGraphicsElement | null;
};

const MetadataPopover = observer(({selectedFlowNodeRef}: Props) => {
  const {data: processInstance} = useProcessInstance();
  const selection = flowNodeSelectionStore.state.selection;
  const elementId = selection?.flowNodeId;
  const elementInstanceKey = selection?.flowNodeInstanceId;

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {data} = useProcessInstanceXml({
    processDefinitionKey,
  });
  const businessObject = elementId ? data?.businessObjects[elementId] : null;

  const {data: statistics} = useFlownodeInstancesStatistics();

  const elementInstancesCount = useElementInstancesCount(elementId);
  const isMultiInstanceBody = selection?.isMultiInstance ?? false;

  const incidentCount =
    statistics?.items.find((stat) => stat.elementId === elementId)?.incidents ??
    0;

  const shouldFetchElementInstances =
    (elementInstancesCount === 1 || isMultiInstanceBody) &&
    !elementInstanceKey &&
    !!processInstance?.processInstanceKey &&
    !!elementId;

  const {data: elementInstance, isLoading: isFetchingInstance} =
    useElementInstance(elementInstanceKey ?? '', {
      enabled: !!elementInstanceKey && !!elementId,
    });

  const {
    data: elementInstancesSearchResult,
    isLoading: isSearchingElementInstances,
  } = useElementInstancesSearch({
    elementId: elementId ?? '',
    processInstanceKey: processInstance?.processInstanceKey ?? '',
    elementType: convertBpmnJsTypeToAPIType(businessObject?.$type),
    enabled: shouldFetchElementInstances,
  });

  const elementInstanceMetadata = useMemo(() => {
    if (elementInstanceKey && elementInstance) {
      return elementInstance;
    }

    if (
      !elementInstanceKey &&
      (elementInstancesCount === 1 || isMultiInstanceBody) &&
      elementInstancesSearchResult?.items?.length === 1
    ) {
      return elementInstancesSearchResult.items[0];
    }

    return null;
  }, [
    elementInstanceKey,
    elementInstance,
    elementInstancesCount,
    elementInstancesSearchResult,
    isMultiInstanceBody,
  ]);

  if (
    elementId === undefined ||
    (shouldFetchElementInstances && isSearchingElementInstances) ||
    (!!elementInstanceKey && isFetchingInstance)
  ) {
    return null;
  }

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
        {elementInstancesCount !== null &&
          elementInstancesCount > 1 &&
          !elementInstanceKey &&
          !isMultiInstanceBody && (
            <>
              <Header
                title={`This element instance triggered ${elementInstancesCount} times`}
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
              elementInstance={elementInstanceMetadata}
              businessObject={businessObject}
            />
            {elementInstanceMetadata.hasIncident && (
              <Incidents
                elementInstanceKey={elementInstanceMetadata.elementInstanceKey}
                elementName={elementInstanceMetadata.elementName}
                elementId={elementId}
              />
            )}
          </>
        )}

        {!elementInstanceMetadata && incidentCount > 0 && (
          <>
            <Divider />
            <MultiIncidents
              count={incidentCount}
              onButtonClick={() => {
                incidentsPanelStore.showIncidentsForElementId(elementId);
              }}
            />
          </>
        )}
      </Stack>
    </Popover>
  );
});

export {MetadataPopover};
