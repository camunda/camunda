/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {LinkButton} from 'modules/components/LinkButton';
import {useState} from 'react';
import {
  SummaryDataKey,
  SummaryDataValue,
  Header,
  Title,
  PeterCaseSummaryHeader,
  PeterCaseSummaryBody,
  Divider,
  Popover,
  CalledProcessValue,
  CalledProcessName,
  LinkContainer,
} from './styled';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {buildMetadata} from './buildMetadata';
import {Paths} from 'modules/routes';
import {Link} from 'modules/components/Link';
import {tracking} from 'modules/tracking';
import {getExecutionDuration} from './getExecutionDuration';
import isNil from 'lodash/isNil';
import {Anchor} from 'modules/components/Anchor/styled';
import {flip, offset} from '@floating-ui/react-dom';

const NULL_METADATA = {
  flowNodeInstanceId: null,
  startDate: null,
  endDate: null,
  calledProcessInstanceId: null,
  calledProcessDefinitionName: null,
  calledDecisionInstanceId: null,
  calledDecisionDefinitionName: null,
  flowNodeType: null,
} as const;

type Props = {
  selectedFlowNodeRef?: SVGGraphicsElement | null;
};

const MetadataPopover = observer(({selectedFlowNodeRef}: Props) => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
  const {metaData} = flowNodeMetaDataStore.state;
  const processInstanceId =
    processInstanceDetailsStore.state.processInstance?.id;

  if (flowNodeId === undefined || metaData === null) {
    return null;
  }

  const businessObject =
    processInstanceDetailsDiagramStore.businessObjects[flowNodeId];
  const flowNodeName = businessObject?.name || flowNodeId;
  const {instanceMetadata, incident, incidentCount} = metaData;

  const {
    flowNodeInstanceId,
    startDate,
    endDate,
    calledProcessInstanceId,
    calledProcessDefinitionName,
    calledDecisionInstanceId,
    calledDecisionDefinitionName,
    flowNodeType,
  } = instanceMetadata ?? NULL_METADATA;
  const rootCauseInstance = incident?.rootCauseInstance || null;
  const rootCauseDecision = incident?.rootCauseDecision || null;

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
      {metaData.instanceCount !== null && metaData.instanceCount > 1 && (
        <>
          <PeterCaseSummaryHeader>
            {`This Flow Node triggered ${metaData.instanceCount} times`}
          </PeterCaseSummaryHeader>
          <PeterCaseSummaryBody>
            To view details for any of these, select one Instance in the
            Instance History.
          </PeterCaseSummaryBody>
        </>
      )}
      {instanceMetadata !== null && (
        <>
          <Header>
            <Title>Details</Title>
            <LinkContainer>
              {!isNil(window.clientConfig?.tasklistUrl) &&
                flowNodeType === 'USER_TASK' && (
                  <Anchor
                    href={window.clientConfig?.tasklistUrl}
                    target="_blank"
                  >
                    Open Tasklist
                  </Anchor>
                )}
              <LinkButton
                size="small"
                onClick={() => {
                  setIsModalVisible(true);
                  tracking.track({
                    eventName: 'flow-node-instance-details-opened',
                  });
                }}
                title="Show more metadata"
              >
                View
              </LinkButton>
            </LinkContainer>
          </Header>

          <SummaryDataKey>Flow Node Instance Key</SummaryDataKey>
          <SummaryDataValue>{flowNodeInstanceId}</SummaryDataValue>
          <SummaryDataKey>Execution Duration</SummaryDataKey>
          <SummaryDataValue>
            {getExecutionDuration(startDate!, endDate)}
          </SummaryDataValue>
          {businessObject?.$type === 'bpmn:CallActivity' &&
            flowNodeType !== 'MULTI_INSTANCE_BODY' && (
              <>
                <SummaryDataKey>Called Process Instance</SummaryDataKey>
                <SummaryDataValue data-testid="called-process-instance">
                  {calledProcessInstanceId ? (
                    <Link
                      to={Paths.processInstance(calledProcessInstanceId)}
                      title={`View ${calledProcessDefinitionName} instance ${calledProcessInstanceId}`}
                    >
                      <CalledProcessValue>
                        <CalledProcessName>
                          {`${calledProcessDefinitionName}`}
                        </CalledProcessName>
                        {` - ${calledProcessInstanceId}`}
                      </CalledProcessValue>
                    </Link>
                  ) : (
                    'None'
                  )}
                </SummaryDataValue>
              </>
            )}
          {businessObject?.$type === 'bpmn:BusinessRuleTask' && (
            <>
              <SummaryDataKey>Called Decision Instance</SummaryDataKey>
              <SummaryDataValue>
                {calledDecisionInstanceId ? (
                  <Link
                    to={Paths.decisionInstance(calledDecisionInstanceId)}
                    title={`View ${calledDecisionDefinitionName} instance ${calledDecisionInstanceId}`}
                  >
                    {`${calledDecisionDefinitionName} - ${calledDecisionInstanceId}`}
                  </Link>
                ) : (
                  calledDecisionDefinitionName ?? '—'
                )}
              </SummaryDataValue>
            </>
          )}
          {incident !== null && (
            <>
              <Divider />
              <Header>
                <Title $variant="incident">Incident</Title>
                <LinkButton
                  size="small"
                  onClick={() => {
                    incidentsStore.clearSelection();
                    incidentsStore.toggleFlowNodeSelection(flowNodeId);
                    incidentsStore.toggleErrorTypeSelection(
                      incident.errorType.id
                    );
                    incidentsStore.setIncidentBarOpen(true);
                  }}
                  title="Show incident"
                >
                  View
                </LinkButton>
              </Header>
              <SummaryDataKey>Type</SummaryDataKey>
              <SummaryDataValue>{incident.errorType.name}</SummaryDataValue>
              {incident.errorMessage !== null && (
                <>
                  <SummaryDataKey>Error Message</SummaryDataKey>
                  <SummaryDataValue $lineClamp={2}>
                    {incident.errorMessage}
                  </SummaryDataValue>
                </>
              )}
              {rootCauseInstance !== null && rootCauseDecision === null && (
                <>
                  <SummaryDataKey>Root Cause Process Instance</SummaryDataKey>
                  <SummaryDataValue>
                    {rootCauseInstance.instanceId === processInstanceId ? (
                      'Current Instance'
                    ) : (
                      <Link
                        to={Paths.processInstance(rootCauseInstance.instanceId)}
                        title={`View root cause instance ${rootCauseInstance.processDefinitionName} - ${rootCauseInstance.instanceId}`}
                      >
                        {`${rootCauseInstance.processDefinitionName} - ${rootCauseInstance.instanceId}`}
                      </Link>
                    )}
                  </SummaryDataValue>
                </>
              )}
              {rootCauseDecision !== null && (
                <>
                  <SummaryDataKey>Root Cause Decision Instance</SummaryDataKey>
                  <SummaryDataValue>
                    <Link
                      to={Paths.decisionInstance(rootCauseDecision.instanceId)}
                      title={`View root cause decision ${rootCauseDecision.decisionName} - ${rootCauseDecision.instanceId}`}
                    >
                      {`${rootCauseDecision.decisionName} - ${rootCauseDecision.instanceId}`}
                    </Link>
                  </SummaryDataValue>
                </>
              )}
            </>
          )}
          <JSONEditorModal
            isVisible={isModalVisible}
            onClose={() => setIsModalVisible(false)}
            title={`Flow Node "${flowNodeName}" ${flowNodeInstanceId} Metadata`}
            value={buildMetadata(metaData.instanceMetadata, incident)}
            readOnly
          />
        </>
      )}
      {incidentCount > 1 && (
        <>
          <Divider />
          <Header>
            <Title aria-label="Incidents" $variant="incident">
              Incidents
            </Title>
            <LinkButton
              size="small"
              onClick={() => {
                incidentsStore.clearSelection();
                incidentsStore.toggleFlowNodeSelection(flowNodeId);
                incidentsStore.setIncidentBarOpen(true);
              }}
              title="Show incidents"
            >
              View
            </LinkButton>
          </Header>
          <SummaryDataValue>
            {`${incidentCount} incidents occurred`}
          </SummaryDataValue>
        </>
      )}
    </Popover>
  );
});

export {MetadataPopover};
