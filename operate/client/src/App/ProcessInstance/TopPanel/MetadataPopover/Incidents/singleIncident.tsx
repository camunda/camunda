/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack} from '@carbon/react';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {Header} from '../Header';
import type {Incident} from '@camunda/camunda-api-zod-schemas/8.8';
import {SummaryDataKey, SummaryDataValue} from '../styled';
import {useDecisionInstancesSearch} from 'modules/queries/decisionInstances/useDecisionInstancesSearch';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {getIncidentErrorName} from 'modules/utils/incidents';

type Props = {
  incident: Incident;
  onButtonClick: () => void;
};

const SingleIncident: React.FC<Props> = ({incident, onButtonClick}) => {
  const {data: processInstance} = useProcessInstance();

  const {data} = useDecisionInstancesSearch(
    {filter: {elementInstanceKey: incident.elementInstanceKey}},
    {enabled: incident.errorType === 'DECISION_EVALUATION_ERROR'},
  );

  const rootCauseDecisionInstance = data?.items[0];
  const errorTypeName = getIncidentErrorName(incident.errorType);
  const labelId = 'metadata-popover-incident-title';

  return (
    <section aria-labelledby={labelId}>
      <Header
        title="Incident"
        titleId={labelId}
        variant="error"
        button={{
          onClick: onButtonClick,
          title: 'Show incident',
          label: 'View',
        }}
      />
      <Stack gap={5}>
        <Stack gap={3} as="dl">
          <SummaryDataKey>Type</SummaryDataKey>
          <SummaryDataValue>{errorTypeName}</SummaryDataValue>
        </Stack>
        {incident.errorMessage !== null && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Error Message</SummaryDataKey>
            <SummaryDataValue $lineClamp={2}>
              {incident.errorMessage}
            </SummaryDataValue>
          </Stack>
        )}
        {incident.errorType !== 'DECISION_EVALUATION_ERROR' && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Root Cause Process Instance</SummaryDataKey>
            <SummaryDataValue>
              {incident.processInstanceKey ===
              processInstance?.processInstanceKey ? (
                'Current Instance'
              ) : (
                <Link
                  to={Paths.processInstance(incident.processInstanceKey)}
                  title={`View root cause instance ${incident.processDefinitionId} - ${incident.processInstanceKey}`}
                >
                  {`${incident.processDefinitionId} - ${incident.processInstanceKey}`}
                </Link>
              )}
            </SummaryDataValue>
          </Stack>
        )}

        {rootCauseDecisionInstance && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Root Cause Decision Instance</SummaryDataKey>
            <SummaryDataValue>
              <Link
                to={Paths.decisionInstance(
                  rootCauseDecisionInstance.decisionEvaluationInstanceKey,
                )}
                title={`View root cause decision ${rootCauseDecisionInstance.decisionDefinitionName} - ${rootCauseDecisionInstance.decisionEvaluationInstanceKey}`}
                aria-label={`View root cause decision ${rootCauseDecisionInstance.decisionDefinitionName} - ${rootCauseDecisionInstance.decisionEvaluationInstanceKey}`}
              >
                {`${rootCauseDecisionInstance.decisionDefinitionName} - ${rootCauseDecisionInstance.decisionEvaluationInstanceKey}`}
              </Link>
            </SummaryDataValue>
          </Stack>
        )}
      </Stack>
    </section>
  );
};

export {SingleIncident};
