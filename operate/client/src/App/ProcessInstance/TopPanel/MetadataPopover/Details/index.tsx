/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack} from '@carbon/react';
import {useMemo} from 'react';
import isNil from 'lodash/isNil';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {Header} from '../Header';
import {SummaryDataKey, SummaryDataValue} from '../styled';
import {getExecutionDuration} from './getExecutionDuration';
import {formatDate} from 'modules/utils/date';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {useProcessInstancesSearch} from 'modules/queries/processInstance/useProcessInstancesSearch';
import {useJobs} from 'modules/queries/jobs/useJobs';
import {useDecisionInstancesSearch} from 'modules/queries/decisionInstances/useDecisionInstancesSearch';

type Props = {
  elementInstance: ElementInstance;
  businessObject?: BusinessObject | null;
};

const Details: React.FC<Props> = ({elementInstance, businessObject}) => {
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
    disabled: !elementInstanceKey,
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

        {job?.retries !== undefined && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Retries Left</SummaryDataKey>
            <SummaryDataValue data-testid="retries-left-count">
              {job.retries}
            </SummaryDataValue>
          </Stack>
        )}

        {job?.worker && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Job Worker</SummaryDataKey>
            <SummaryDataValue>{job.worker}</SummaryDataValue>
          </Stack>
        )}

        {job?.type && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Job Type</SummaryDataKey>
            <SummaryDataValue>{job.type}</SummaryDataValue>
          </Stack>
        )}

        {job?.jobKey && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Job Key</SummaryDataKey>
            <SummaryDataValue>{job.jobKey}</SummaryDataValue>
          </Stack>
        )}

        {startDate && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Start Date</SummaryDataKey>
            <SummaryDataValue>{formatDate(startDate)}</SummaryDataValue>
          </Stack>
        )}

        {endDate && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>End Date</SummaryDataKey>
            <SummaryDataValue>{formatDate(endDate)}</SummaryDataValue>
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
    </>
  );
};

export {Details};
