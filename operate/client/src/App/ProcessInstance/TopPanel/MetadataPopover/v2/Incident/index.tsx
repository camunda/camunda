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
import {type MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import {Header} from '../../Header';
import type {
  Incident as IncidentDto,
  DecisionInstance,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {SummaryDataKey, SummaryDataValue} from '../../styled';
import {resolveIncidentErrorType} from './resolveIncidentErrorType';

type Props = {
  incidentV2: IncidentDto;
  incident: MetaDataDto['incident'];
  rootCauseDecisionInstance: DecisionInstance | null;
  processInstanceId?: string;
  onButtonClick: () => void;
};

const Incident: React.FC<Props> = ({
  incidentV2,
  incident,
  rootCauseDecisionInstance,
  processInstanceId,
  onButtonClick,
}) => {
  if (!incident || !incidentV2) {
    return null;
  }

  //TODO will be handled separately in #35529
  const {rootCauseInstance} = incident;

  const errorType = resolveIncidentErrorType(incidentV2.errorType);

  return (
    <>
      <Header
        title="Incident"
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
          <SummaryDataValue>{errorType.name}</SummaryDataValue>
        </Stack>
        {incidentV2.errorMessage !== null && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Error Message</SummaryDataKey>
            <SummaryDataValue $lineClamp={2}>
              {incidentV2.errorMessage}
            </SummaryDataValue>
          </Stack>
        )}
        {!!rootCauseInstance && !rootCauseDecisionInstance && (
          <Stack gap={3} as="dl">
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
          </Stack>
        )}
        {rootCauseDecisionInstance !== null && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Root Cause Decision Instance</SummaryDataKey>
            <SummaryDataValue>
              <Link
                to={Paths.decisionInstance(
                  rootCauseDecisionInstance.decisionInstanceKey,
                )}
                title={`View root cause decision ${rootCauseDecisionInstance.decisionDefinitionName} - ${rootCauseDecisionInstance.decisionInstanceKey}`}
                aria-label={`View root cause decision ${rootCauseDecisionInstance.decisionDefinitionName} - ${rootCauseDecisionInstance.decisionInstanceKey}`}
              >
                {`${rootCauseDecisionInstance.decisionDefinitionName} - ${rootCauseDecisionInstance.decisionInstanceKey}`}
              </Link>
            </SummaryDataValue>
          </Stack>
        )}
      </Stack>
    </>
  );
};

export {Incident};
