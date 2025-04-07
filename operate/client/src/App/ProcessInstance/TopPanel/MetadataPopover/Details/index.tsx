/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Stack} from '@carbon/react';
import isNil from 'lodash/isNil';
import {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {Header} from '../Header';
import {SummaryDataKey, SummaryDataValue} from '../styled';
import {getExecutionDuration} from './getExecutionDuration';
import {buildMetadata} from './buildMetadata';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';

type Props = {
  metaData: MetaDataDto;
  flowNodeId: string;
};

const NULL_METADATA = {
  flowNodeInstanceId: null,
  startDate: null,
  endDate: null,
  calledProcessInstanceId: null,
  calledProcessDefinitionName: null,
  calledDecisionInstanceId: null,
  calledDecisionDefinitionName: null,
  flowNodeType: null,
  jobRetries: null,
} as const;

const Details: React.FC<Props> = ({metaData, flowNodeId}) => {
  const [isModalVisible, setIsModalVisible] = useState(false);

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {data} = useProcessInstanceXml({
    processDefinitionKey,
  });
  const businessObject = data?.businessObjects[flowNodeId];

  const flowNodeName = businessObject?.name || flowNodeId;

  const {instanceMetadata, incident} = metaData;
  const {
    flowNodeInstanceId,
    startDate,
    endDate,
    calledProcessInstanceId,
    calledProcessDefinitionName,
    calledDecisionInstanceId,
    calledDecisionDefinitionName,
    flowNodeType,
    jobRetries,
  } = instanceMetadata ?? NULL_METADATA;

  return (
    <>
      <Header
        title="Details"
        link={
          !isNil(window.clientConfig?.tasklistUrl) &&
          flowNodeType === 'USER_TASK'
            ? {
                href: window.clientConfig!.tasklistUrl,
                label: 'Open Tasklist',
                onClick: () => {
                  tracking.track({
                    eventName: 'open-tasklist-link-clicked',
                  });
                },
              }
            : undefined
        }
        button={{
          title: 'Show more metadata',
          label: 'View',
          onClick: () => {
            setIsModalVisible(true);
            tracking.track({
              eventName: 'flow-node-instance-details-opened',
            });
          },
        }}
      />
      <Stack gap={5}>
        <Stack gap={3} as="dl">
          <SummaryDataKey>Flow Node Instance Key</SummaryDataKey>
          <SummaryDataValue>{flowNodeInstanceId}</SummaryDataValue>
        </Stack>
        <Stack gap={3} as="dl">
          <SummaryDataKey>Execution Duration</SummaryDataKey>
          <SummaryDataValue>
            {getExecutionDuration(startDate!, endDate)}
          </SummaryDataValue>
        </Stack>

        {jobRetries !== null && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Retries Left</SummaryDataKey>
            <SummaryDataValue data-testid="retries-left-count">
              {jobRetries}
            </SummaryDataValue>
          </Stack>
        )}

        {businessObject?.$type === 'bpmn:CallActivity' &&
          flowNodeType !== 'MULTI_INSTANCE_BODY' && (
            <Stack gap={3} as="dl">
              <SummaryDataKey>Called Process Instance</SummaryDataKey>
              <SummaryDataValue data-testid="called-process-instance">
                {calledProcessInstanceId ? (
                  <Link
                    to={Paths.processInstance(calledProcessInstanceId)}
                    title={`View ${calledProcessDefinitionName} instance ${calledProcessInstanceId}`}
                    aria-label={`View ${calledProcessDefinitionName} instance ${calledProcessInstanceId}`}
                  >
                    {`${calledProcessDefinitionName} - ${calledProcessInstanceId}`}
                  </Link>
                ) : (
                  'None'
                )}
              </SummaryDataValue>
            </Stack>
          )}

        {businessObject?.$type === 'bpmn:BusinessRuleTask' && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Called Decision Instance</SummaryDataKey>
            <SummaryDataValue>
              {calledDecisionInstanceId ? (
                <Link
                  to={Paths.decisionInstance(calledDecisionInstanceId)}
                  title={`View ${calledDecisionDefinitionName} instance ${calledDecisionInstanceId}`}
                  aria-label={`View ${calledDecisionDefinitionName} instance ${calledDecisionInstanceId}`}
                >
                  {`${calledDecisionDefinitionName} - ${calledDecisionInstanceId}`}
                </Link>
              ) : (
                (calledDecisionDefinitionName ?? '—')
              )}
            </SummaryDataValue>
          </Stack>
        )}
      </Stack>
      <JSONEditorModal
        isVisible={isModalVisible}
        onClose={() => setIsModalVisible(false)}
        title={`Flow Node "${flowNodeName}" ${flowNodeInstanceId} Metadata`}
        value={buildMetadata(instanceMetadata, incident)}
        readOnly
      />
    </>
  );
};

export {Details};
