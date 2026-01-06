/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {useGetIncidentsByElementInstance} from 'modules/queries/incidents/useGetIncidentsByElementInstance';
import {useGetUserTaskByElementInstance} from 'modules/queries/userTasks/useGetUserTaskByElementInstance';
import {useSearchMessageSubscriptions} from 'modules/queries/messageSubscriptions/useSearchMessageSubscriptions';
import {useDecisionDefinition} from 'modules/queries/decisionDefinitions/useDecisionDefinition';
import {createMetadataJson} from './createMetadataJson';
import type {
  ElementInstance,
  Job,
  ProcessInstance,
  DecisionInstance,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {getIncidentErrorName} from 'modules/utils/incidents';

type Props = {
  elementInstance: ElementInstance;
  job?: Job;
  calledProcessInstance?: ProcessInstance;
  calledDecisionInstance?: DecisionInstance;
  isVisible: boolean;
  onClose: () => void;
};

const DetailsModal: React.FC<Props> = ({
  elementInstance,
  job,
  calledProcessInstance,
  calledDecisionInstance,
  isVisible,
  onClose,
}) => {
  const {type, elementInstanceKey, elementName} = elementInstance;

  const {data: userTask} = useGetUserTaskByElementInstance(
    elementInstanceKey ?? '',
    {
      enabled: isVisible && !!elementInstanceKey && type === 'USER_TASK',
    },
  );

  const {data: messageSubscriptionSearchResult} = useSearchMessageSubscriptions(
    {
      filter: {
        elementInstanceKey: elementInstanceKey ?? '',
      },
    },
    {
      enabled: isVisible && !!elementInstanceKey,
    },
  );

  const calledDecisionDefinitionId =
    calledDecisionInstance?.rootDecisionDefinitionKey;

  const {data: calledDecisionDefinition} = useDecisionDefinition(
    calledDecisionDefinitionId ?? '',
    {
      enabled: isVisible && !!calledDecisionDefinitionId,
    },
  );

  const messageSubscription = messageSubscriptionSearchResult?.items?.[0];

  const {data: incident} = useGetIncidentsByElementInstance(
    elementInstanceKey,
    {
      enabled: isVisible && elementInstance.hasIncident,
      select: (data) => {
        const singleIncident = data.items.at(0) ?? null;
        if (data.page.totalItems !== 1 || !singleIncident) {
          return null;
        }

        return {
          errorTypeName: getIncidentErrorName(singleIncident.errorType),
          errorMessage: singleIncident.errorMessage,
        };
      },
    },
  );

  const metadataJson = useMemo(
    () =>
      createMetadataJson(
        elementInstance,
        incident ?? null,
        job,
        calledProcessInstance,
        messageSubscription,
        calledDecisionDefinition,
        calledDecisionInstance,
        userTask ?? null,
      ),
    [
      elementInstance,
      incident,
      job,
      calledProcessInstance,
      messageSubscription,
      calledDecisionDefinition,
      calledDecisionInstance,
      userTask,
    ],
  );

  return (
    <JSONEditorModal
      isVisible={isVisible}
      onClose={onClose}
      title={`Element "${elementName}" ${elementInstanceKey} Metadata`}
      value={metadataJson}
      readOnly
    />
  );
};

export {DetailsModal};
