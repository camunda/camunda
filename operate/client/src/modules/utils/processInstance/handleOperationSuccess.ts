/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQueryClient} from '@tanstack/react-query';
import {useNavigate} from 'react-router-dom';
import {notificationsStore} from 'modules/stores/notifications';
import {tracking} from 'modules/tracking';
import {Locations} from 'modules/Routes';
import {queryKeys} from 'modules/queries/queryKeys';
import type {OperationEntityType} from 'modules/types/operate';

type HandleOperationSuccessOptions = {
  operationType: OperationEntityType;
  source: 'instances-list' | 'instance-header';
  onInvalidateQueries?: () => void;
};

function useHandleOperationSuccess() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return ({
    operationType,
    source,
    onInvalidateQueries,
  }: HandleOperationSuccessOptions) => {
    if (onInvalidateQueries) {
      onInvalidateQueries();
    } else {
      queryClient.invalidateQueries({
        queryKey: queryKeys.processInstances.base(),
      });
    }

    tracking.track({
      eventName: 'single-operation',
      operationType,
      source,
    });

    if (operationType === 'DELETE_PROCESS_INSTANCE') {
      if (source === 'instance-header') {
        navigate(
          Locations.processes({
            active: true,
            incidents: true,
          }),
          {replace: true},
        );
      }

      notificationsStore.displayNotification({
        kind: 'success',
        title: 'Instance is scheduled for deletion',
        isDismissable: true,
      });
    }
  };
}

export {useHandleOperationSuccess};
