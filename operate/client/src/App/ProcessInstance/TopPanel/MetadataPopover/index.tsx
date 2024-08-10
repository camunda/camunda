/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Divider, Popover, Content} from './styled';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {flip, offset} from '@floating-ui/react-dom';
import {Header} from './Header';
import {Stack} from '@carbon/react';
import {Details} from './Details';
import {Incident} from './Incident';
import {MultiIncidents} from './MultiIncidents';

type Props = {
  selectedFlowNodeRef?: SVGGraphicsElement | null;
};

const MetadataPopover = observer(({selectedFlowNodeRef}: Props) => {
  const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
  const {metaData} = flowNodeMetaDataStore.state;

  if (flowNodeId === undefined || metaData === null) {
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
        {metaData.instanceCount !== null && metaData.instanceCount > 1 && (
          <>
            <Header
              title={`This Flow Node triggered ${metaData.instanceCount} times`}
            />
            <Content>
              To view details for any of these, select one Instance in the
              Instance History.
            </Content>
          </>
        )}

        {instanceMetadata !== null && (
          <>
            <Details metaData={metaData} flowNodeId={flowNodeId} />
            {incident !== null && (
              <>
                <Divider />
                <Incident
                  processInstanceId={
                    processInstanceDetailsStore.state.processInstance?.id
                  }
                  incident={incident}
                  onButtonClick={() => {
                    incidentsStore.clearSelection();
                    incidentsStore.toggleFlowNodeSelection(flowNodeId);
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
                incidentsStore.toggleFlowNodeSelection(flowNodeId);
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
