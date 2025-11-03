/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack} from '@carbon/react';
import {useState} from 'react';
import isNil from 'lodash/isNil';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {Header} from '../../Header';
import {SummaryDataKey, SummaryDataValue} from '../../styled';
import {getExecutionDuration} from '../../Details/getExecutionDuration';
import {type V2MetaDataDto} from '../types';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {DetailsModal} from './DetailsModal';

type Props = {
  metaData: V2MetaDataDto;
  elementId: string;
  businessObject?: BusinessObject | null;
};

const NULL_METADATA = {
  elementInstanceKey: null,
  startDate: null,
  endDate: null,
  calledProcessInstanceId: null,
  calledProcessDefinitionName: null,
  calledDecisionInstanceId: null,
  calledDecisionDefinitionName: null,
  type: null,
  jobRetries: null,
} as const;

const Details: React.FC<Props> = ({metaData, elementId, businessObject}) => {
  const [isModalVisible, setIsModalVisible] = useState(false);

  const elementName = businessObject?.name || elementId;

  const {instanceMetadata} = metaData;
  const {
    elementInstanceKey,
    startDate,
    endDate,
    calledProcessInstanceId,
    calledProcessDefinitionName,
    calledDecisionInstanceId,
    calledDecisionDefinitionName,
    type,
    jobRetries,
  } = instanceMetadata ?? NULL_METADATA;

  return (
    <>
      <Header
        title="Details"
        link={
          !isNil(window.clientConfig?.tasklistUrl) && type === 'USER_TASK'
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
          <SummaryDataKey>Element Instance Key</SummaryDataKey>
          <SummaryDataValue>{elementInstanceKey}</SummaryDataValue>
        </Stack>
        <Stack gap={3} as="dl">
          <SummaryDataKey>Execution Duration</SummaryDataKey>
          <SummaryDataValue>
            {startDate ? getExecutionDuration(startDate, endDate) : '—'}
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
          type !== 'MULTI_INSTANCE_BODY' && (
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
      {elementInstanceKey !== null && instanceMetadata !== null && (
        <DetailsModal
          elementInstanceKey={elementInstanceKey}
          elementName={elementName}
          instanceMetadata={instanceMetadata}
          isVisible={isModalVisible}
          setIsVisible={setIsModalVisible}
        />
      )}
    </>
  );
};

export {Details};
