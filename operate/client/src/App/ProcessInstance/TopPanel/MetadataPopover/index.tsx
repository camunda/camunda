/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Popover, Content, Divider} from './styled';
import {observer} from 'mobx-react';
import {flip, offset} from '@floating-ui/react-dom';
import {Header} from './Header';
import {Stack} from '@carbon/react';
import {MultiIncidents} from './Incidents/multiIncidents';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {Details} from './Details';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {Incidents} from './Incidents';
import {useElementInstancesCount} from 'modules/hooks/useElementInstancesCount';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';

type Props = {
  selectedFlowNodeRef?: SVGGraphicsElement | null;
};

const MetadataPopover = observer(({selectedFlowNodeRef}: Props) => {
  const {
    selectedElementId,
    selectedElementInstanceKey,
    resolvedElementInstance,
    isFetchingElement,
    isSelectedInstanceMultiInstanceBody,
  } = useProcessInstanceElementSelection();
  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {data} = useProcessInstanceXml({
    processDefinitionKey,
  });
  const labelId = 'metadata-popover-instances-title';

  const businessObject = selectedElementId
    ? data?.businessObjects[selectedElementId]
    : null;
  const {data: statistics} = useFlownodeInstancesStatistics();
  let elementInstancesCount = useElementInstancesCount(
    selectedElementId ?? undefined,
  );

  if (isSelectedInstanceMultiInstanceBody) {
    elementInstancesCount = 1;
  }
  const incidentCount =
    statistics?.items.find((stat) => stat.elementId === selectedElementId)
      ?.incidents ?? 0;

  if (
    selectedElementId === null ||
    (!!selectedElementInstanceKey && isFetchingElement)
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
          !selectedElementInstanceKey && (
            <section aria-labelledby={labelId}>
              <Header
                title={`This element instance triggered ${elementInstancesCount} times`}
                titleId={labelId}
              />
              <Content>
                To view details for any of these, select one Instance in the
                Instance History.
              </Content>
            </section>
          )}

        {resolvedElementInstance && (
          <>
            <Details
              elementInstance={resolvedElementInstance}
              businessObject={businessObject}
            />
            {resolvedElementInstance.hasIncident && (
              <Incidents
                elementInstanceKey={resolvedElementInstance.elementInstanceKey}
                elementName={resolvedElementInstance.elementName}
                elementId={selectedElementId}
              />
            )}
          </>
        )}

        {!resolvedElementInstance && incidentCount > 0 && (
          <>
            <Divider />
            <MultiIncidents
              count={incidentCount}
              onButtonClick={() => {
                incidentsPanelStore.showIncidentsForElementId(
                  selectedElementId,
                );
              }}
            />
          </>
        )}
      </Stack>
    </Popover>
  );
});

export {MetadataPopover};
