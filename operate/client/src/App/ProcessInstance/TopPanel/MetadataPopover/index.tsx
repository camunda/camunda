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
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {useMemo} from 'react';
import {Details} from './Details';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {Incidents} from './Incidents';
import {useElementSelection} from 'modules/hooks/useElementSelection';
import {useElementInstanceResolution} from 'modules/hooks/useElementInstanceResolution';

type Props = {
  selectedFlowNodeRef?: SVGGraphicsElement | null;
};

const MetadataPopover = observer(({selectedFlowNodeRef}: Props) => {
  const {elementId, elementInstanceKey} = useElementSelection();
  const elementInstance = useElementInstanceResolution();

  const selection = flowNodeSelectionStore.state.selection;
  const isMultiInstance = selection?.isMultiInstance ?? false;

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {data} = useProcessInstanceXml({
    processDefinitionKey,
  });
  const businessObject = elementId ? data?.businessObjects[elementId] : null;

  const {data: statistics} = useFlownodeInstancesStatistics();

  const instanceCount = useMemo(() => {
    if (!statistics?.items || !elementId) {
      return null;
    }
    const elementStats = statistics.items.find(
      (stat) => stat.elementId === elementId,
    );
    if (!elementStats) {
      return 0;
    }
    if (isMultiInstance) {
      return 1;
    }
    return (
      elementStats.active +
      elementStats.completed +
      elementStats.canceled +
      elementStats.incidents
    );
  }, [statistics, elementId, isMultiInstance]);

  const incidentCount =
    statistics?.items.find((stat) => stat.elementId === elementId)?.incidents ??
    0;

  if (elementId === null) {
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
        {instanceCount !== null && instanceCount > 1 && !elementInstanceKey && (
          <>
            <Header
              title={`This element instance triggered ${instanceCount} times`}
            />
            <Content>
              To view details for any of these, select one Instance in the
              Instance History.
            </Content>
          </>
        )}

        {elementInstance && elementId && (
          <>
            <Details
              elementInstance={elementInstance}
              businessObject={businessObject}
            />
            {elementInstance.hasIncident && (
              <Incidents
                elementInstanceKey={elementInstance.elementInstanceKey}
                elementName={elementInstance.elementName}
                elementId={elementId}
              />
            )}
          </>
        )}

        {!elementInstance && incidentCount > 0 && (
          <>
            <Divider />
            <MultiIncidents
              count={incidentCount}
              onButtonClick={() => {
                incidentsPanelStore.setPanelOpen(true);
              }}
            />
          </>
        )}
      </Stack>
    </Popover>
  );
});

export {MetadataPopover};
