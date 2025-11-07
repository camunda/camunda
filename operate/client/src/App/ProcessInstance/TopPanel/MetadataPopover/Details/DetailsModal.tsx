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
import {resolveIncidentErrorType} from '../Incidents/resolveIncidentErrorType';
import type {
  ElementInstance,
  Job,
  ProcessInstance,
  DecisionInstance,
} from '@camunda/camunda-api-zod-schemas/8.8';

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

  const {data} = useGetIncidentsByElementInstance(elementInstanceKey, {
    enabled: isVisible && elementInstance.hasIncident,
  });
  const singleIncident = data?.page.totalItems === 1 ? data?.items[0] : null;

  const incident = useMemo(
    () =>
      singleIncident !== null
        ? {
            errorType: resolveIncidentErrorType(singleIncident.errorType),
            errorMessage: singleIncident.errorMessage,
          }
        : null,
    [singleIncident],
  );

  const metadataJson = useMemo(
    () =>
      createMetadataJson(
        elementInstance,
        incident,
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
