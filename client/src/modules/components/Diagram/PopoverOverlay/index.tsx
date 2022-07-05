/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {usePopper} from 'react-popper';
import {createPortal} from 'react-dom';
import CodeModal from 'modules/components/CodeModal';
import {LinkButton} from 'modules/components/LinkButton';
import {Fragment, useRef, useState} from 'react';
import {
  Popover,
  SummaryDataKey,
  SummaryDataValue,
  Header,
  IncidentTitle,
  Title,
  PeterCaseSummaryHeader,
  PeterCaseSummaryBody,
  Divider,
  Arrow,
  Popper,
} from './styled';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {beautifyMetadata} from './beautifyMetadata';
import {getModalHeadline} from './getModalHeadline';
import {Paths} from 'modules/routes';
import {Link} from 'modules/components/Link';
import {tracking} from 'modules/tracking';

type Props = {
  selectedFlowNodeRef: SVGGraphicsElement | null;
};

const PopoverOverlay = observer(({selectedFlowNodeRef}: Props) => {
  const popperElementRef = useRef<HTMLDivElement>(null);
  const [arrow, setArrow] = useState<HTMLElement | null>(null);
  const [isModalVisible, setIsModalVisible] = useState(false);
  const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
  const {metaData} = flowNodeMetaDataStore.state;
  const processInstanceId =
    processInstanceDetailsStore.state.processInstance?.id;

  const {styles, attributes} = usePopper(
    selectedFlowNodeRef,
    popperElementRef.current,
    {
      modifiers: [
        {
          name: 'offset',
          options: {
            offset: [0, 10],
          },
        },
        {
          name: 'flip',
          options: {
            fallbackPlacements: ['top', 'right', 'left'],
          },
        },
        {
          name: 'arrow',
          options: {
            element: arrow,
          },
        },
      ],
    }
  );

  if (flowNodeId === undefined || metaData === null) {
    return null;
  }

  const flowNodeMetaData =
    processInstanceDetailsDiagramStore.getMetaData(flowNodeId);
  const flowNodeName = flowNodeMetaData?.name || flowNodeId;
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
  } = instanceMetadata || {};
  const rootCauseInstance = incident?.rootCauseInstance || null;
  const rootCauseDecision = incident?.rootCauseDecision || null;

  return selectedFlowNodeRef !== null
    ? createPortal(
        <Popper
          ref={popperElementRef}
          style={styles.popper}
          {...attributes.popper}
        >
          <Arrow ref={setArrow} style={styles.arrow} {...attributes.arrow} />
          <Popover data-testid="popover">
            {metaData.instanceCount !== null && metaData.instanceCount > 1 && (
              <>
                <PeterCaseSummaryHeader>
                  {`There are ${metaData.instanceCount} Instances`}
                </PeterCaseSummaryHeader>
                <PeterCaseSummaryBody>
                  To view details for any of these,
                  <br />
                  select one Instance in the Instance History.
                </PeterCaseSummaryBody>
              </>
            )}
            {instanceMetadata !== null && (
              <>
                <Header>
                  <Title>Details</Title>
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
                </Header>

                <SummaryDataKey>Flow Node Instance Key</SummaryDataKey>
                <SummaryDataValue>
                  {metaData.breadcrumb.map((item) => (
                    <Fragment key={`${flowNodeId}-${item.flowNodeType}`}>
                      <LinkButton
                        size="small"
                        data-testid="select-flownode"
                        onClick={() =>
                          flowNodeSelectionStore.selectFlowNode({
                            flowNodeId,
                            flowNodeType: item.flowNodeType,
                            isMultiInstance:
                              item.flowNodeType === 'MULTI_INSTANCE_BODY',
                          })
                        }
                      >
                        {flowNodeName}
                        {item.flowNodeType === 'MULTI_INSTANCE_BODY'
                          ? ' (Multi Instance)'
                          : ''}
                      </LinkButton>
                      {' › '}
                    </Fragment>
                  ))}
                  <span>{flowNodeInstanceId}</span>
                </SummaryDataValue>
                <SummaryDataKey>Start Date</SummaryDataKey>
                <SummaryDataValue>{startDate}</SummaryDataValue>
                <SummaryDataKey>End Date</SummaryDataKey>
                <SummaryDataValue>{endDate || '—'}</SummaryDataValue>
                {flowNodeMetaData?.type.elementType === 'TASK_CALL_ACTIVITY' &&
                  flowNodeType !== 'MULTI_INSTANCE_BODY' && (
                    <>
                      <SummaryDataKey>Called Process Instance</SummaryDataKey>
                      <SummaryDataValue>
                        {calledProcessInstanceId ? (
                          <Link
                            to={Paths.processInstance(calledProcessInstanceId)}
                            title={`View ${calledProcessDefinitionName} instance ${calledProcessInstanceId}`}
                          >
                            {`${calledProcessDefinitionName} - ${calledProcessInstanceId}`}
                          </Link>
                        ) : (
                          'None'
                        )}
                      </SummaryDataValue>
                    </>
                  )}
                {flowNodeMetaData?.type.elementType ===
                  'TASK_BUSINESS_RULE' && (
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
                      <IncidentTitle>Incident</IncidentTitle>
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
                    <SummaryDataValue>
                      {incident.errorType.name}
                    </SummaryDataValue>
                    {incident.errorMessage !== null && (
                      <>
                        <SummaryDataKey>Error Message</SummaryDataKey>
                        <SummaryDataValue>
                          {incident.errorMessage}
                        </SummaryDataValue>
                      </>
                    )}
                    {rootCauseInstance !== null && rootCauseDecision === null && (
                      <>
                        <SummaryDataKey>
                          Root Cause Process Instance
                        </SummaryDataKey>
                        <SummaryDataValue>
                          {rootCauseInstance.instanceId ===
                          processInstanceId ? (
                            'Current Instance'
                          ) : (
                            <Link
                              to={Paths.processInstance(
                                rootCauseInstance.instanceId
                              )}
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
                        <SummaryDataKey>
                          Root Cause Decision Instance
                        </SummaryDataKey>
                        <SummaryDataValue>
                          <Link
                            to={Paths.decisionInstance(
                              rootCauseDecision.instanceId
                            )}
                            title={`View root cause decision ${rootCauseDecision.decisionName} - ${rootCauseDecision.instanceId}`}
                          >
                            {`${rootCauseDecision.decisionName} - ${rootCauseDecision.instanceId}`}
                          </Link>
                        </SummaryDataValue>
                      </>
                    )}
                  </>
                )}

                <CodeModal
                  handleModalClose={() => setIsModalVisible(false)}
                  isModalVisible={isModalVisible}
                  headline={getModalHeadline({flowNodeName, metaData})}
                  initialValue={beautifyMetadata(
                    metaData.instanceMetadata,
                    incident
                  )}
                  mode="view"
                />
              </>
            )}
            {incidentCount > 1 && (
              <>
                <Divider />
                <Header>
                  <IncidentTitle aria-label="Incidents">
                    Incidents
                  </IncidentTitle>
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
                  {`${incidentCount} incidents occured`}
                </SummaryDataValue>
              </>
            )}
          </Popover>
        </Popper>,
        document.body
      )
    : null;
});

export {PopoverOverlay};
