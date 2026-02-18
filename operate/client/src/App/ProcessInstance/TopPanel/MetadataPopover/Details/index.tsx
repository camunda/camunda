/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack} from '@carbon/react';
import {useState, useMemo} from 'react';
import isNil from 'lodash/isNil';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {Header} from '../Header';
import {SummaryDataKey, SummaryDataValue, SummaryText} from '../styled';
import {getExecutionDuration} from './getExecutionDuration';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {DetailsModal} from './DetailsModal';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {useProcessInstancesSearch} from 'modules/queries/processInstance/useProcessInstancesSearch';
import {useJobs} from 'modules/queries/jobs/useJobs';
import {useDecisionInstancesSearch} from 'modules/queries/decisionInstances/useDecisionInstancesSearch';
import {isCamundaUserTask} from 'modules/bpmn-js/utils/isCamundaUserTask';

type Props = {
  elementInstance: ElementInstance;
  businessObject?: BusinessObject | null;
};

const Details: React.FC<Props> = ({elementInstance, businessObject}) => {
  const [isModalVisible, setIsModalVisible] = useState(false);

  const {startDate, endDate, type, elementInstanceKey} = elementInstance;

  const {data: calledProcessInstancesSearchResult} = useProcessInstancesSearch(
    {
      filter: {
        parentElementInstanceKey: elementInstanceKey ?? '',
      },
    },
    {
      enabled: !!elementInstanceKey && type === 'CALL_ACTIVITY',
    },
  );

  const {data: jobSearchResult} = useJobs({
    payload: {
      filter: {
        elementInstanceKey: elementInstanceKey ?? '',
        listenerEventType: 'UNSPECIFIED',
      },
    },
    enabled: !!elementInstanceKey,
    select: (data) => data.pages?.flatMap((page) => page.items),
  });

  const {data: decisionInstanceSearchResult} = useDecisionInstancesSearch(
    {
      filter: {
        elementInstanceKey: elementInstanceKey ?? '',
      },
    },
    {
      enabled: !!elementInstanceKey && type === 'BUSINESS_RULE_TASK',
    },
  );

  const calledDecisionInstance = useMemo(
    () =>
      decisionInstanceSearchResult?.items?.find(
        (instance) =>
          instance.rootDecisionDefinitionKey === instance.decisionDefinitionKey,
      ),
    [decisionInstanceSearchResult],
  );

  const calledProcessInstance = calledProcessInstancesSearchResult?.items?.[0];
  const job = jobSearchResult?.[0];

  return (
    <section aria-labelledby="metadata-popover-details-title">
      <Header
        title="Details"
        titleId="metadata-popover-details-title"
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
        {type === 'USER_TASK' && !isCamundaUserTask(businessObject) && (
          <SummaryText>
            User tasks with job worker implementation are deprecated. Consider
            migrating to Camunda user tasks.
          </SummaryText>
        )}
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

        {job?.retries !== undefined && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Retries Left</SummaryDataKey>
            <SummaryDataValue data-testid="retries-left-count">
              {job.retries}
            </SummaryDataValue>
          </Stack>
        )}

        {businessObject?.$type === 'bpmn:CallActivity' &&
          type !== 'MULTI_INSTANCE_BODY' && (
            <Stack gap={3} as="dl">
              <SummaryDataKey>Called Process Instance</SummaryDataKey>
              <SummaryDataValue data-testid="called-process-instance">
                {calledProcessInstance ? (
                  <Link
                    to={Paths.processInstance(
                      calledProcessInstance.processInstanceKey,
                    )}
                    title={`View ${calledProcessInstance.processDefinitionName} instance ${calledProcessInstance.processInstanceKey}`}
                    aria-label={`View ${calledProcessInstance.processDefinitionName} instance ${calledProcessInstance.processInstanceKey}`}
                  >
                    {`${calledProcessInstance.processDefinitionName} - ${calledProcessInstance.processInstanceKey}`}
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
              {calledDecisionInstance ? (
                <Link
                  to={Paths.decisionInstance(
                    calledDecisionInstance.decisionEvaluationInstanceKey,
                  )}
                  title={`View ${calledDecisionInstance.decisionDefinitionName} instance ${calledDecisionInstance.decisionEvaluationInstanceKey}`}
                  aria-label={`View ${calledDecisionInstance.decisionDefinitionName} instance ${calledDecisionInstance.decisionEvaluationInstanceKey}`}
                >
                  {`${calledDecisionInstance.decisionDefinitionName} - ${calledDecisionInstance.decisionEvaluationInstanceKey}`}
                </Link>
              ) : (
                '—'
              )}
            </SummaryDataValue>
          </Stack>
        )}
      </Stack>
      {elementInstanceKey !== null && (
        <DetailsModal
          elementInstance={elementInstance}
          job={job}
          calledProcessInstance={calledProcessInstance}
          calledDecisionInstance={calledDecisionInstance}
          isVisible={isModalVisible}
          onClose={() => setIsModalVisible(false)}
        />
      )}
    </section>
  );
};

export {Details};
