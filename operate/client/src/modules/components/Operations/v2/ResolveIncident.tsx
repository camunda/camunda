/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type ProcessInstance} from '@vzeta/camunda-api-zod-schemas/8.8';
import {OperationItem} from 'modules/components/OperationItem';
import {useCreateIncidentResolutionBatchOperation} from 'modules/mutations/processInstance/useCreateIncidentResolutionBatchOperation';
import {notificationsStore} from 'modules/stores/notifications';

type Props = {
  processInstanceKey: ProcessInstance['processInstanceKey'];
};

const ResolveIncident: React.FC<Props> = ({processInstanceKey}) => {
  const {mutate, isPending} = useCreateIncidentResolutionBatchOperation(
    processInstanceKey,
    {
      onError: (error) =>
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Failed to retry process instance',
          subtitle: error.message,
          isDismissable: true,
        }),
    },
  );

  return (
    <OperationItem
      type="RESOLVE_INCIDENT"
      onClick={() => {
        mutate();
      }}
      title={`Retry Instance ${processInstanceKey}`}
      disabled={isPending}
      size="sm"
    />
  );
};

export {ResolveIncident};
