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
import {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import {Header} from '../Header';
import {SummaryDataKey, SummaryDataValue} from '../styled';

type Props = {
  incident: MetaDataDto['incident'];
  processInstanceId?: string;
  onButtonClick: () => void;
};

const Incident: React.FC<Props> = ({
  incident,
  processInstanceId,
  onButtonClick,
}) => {
  if (incident === null) {
    return null;
  }

  const {rootCauseDecision, rootCauseInstance} = incident;

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
          <SummaryDataValue>{incident.errorType.name}</SummaryDataValue>
        </Stack>
        {incident.errorMessage !== null && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Error Message</SummaryDataKey>
            <SummaryDataValue $lineClamp={2}>
              {incident.errorMessage}
            </SummaryDataValue>
          </Stack>
        )}
        {rootCauseInstance !== null && rootCauseDecision === null && (
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
        {rootCauseDecision !== null && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Root Cause Decision Instance</SummaryDataKey>
            <SummaryDataValue>
              <Link
                to={Paths.decisionInstance(rootCauseDecision.instanceId)}
                title={`View root cause decision ${rootCauseDecision.decisionName} - ${rootCauseDecision.instanceId}`}
                aria-label={`View root cause decision ${rootCauseDecision.decisionName} - ${rootCauseDecision.instanceId}`}
              >
                {`${rootCauseDecision.decisionName} - ${rootCauseDecision.instanceId}`}
              </Link>
            </SummaryDataValue>
          </Stack>
        )}
      </Stack>
    </>
  );
};

export {Incident};
