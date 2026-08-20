/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNavigate} from 'react-router-dom';
import {notificationsStore} from 'modules/stores/notifications';
import {formatOperationType} from 'modules/utils/formatOperationType';
import {Paths} from 'modules/Routes';

function useBatchOperationSuccessNotification() {
  const navigate = useNavigate();

  return (batchOperationType: string, batchOperationKey: string) => {
    notificationsStore.displayNotification({
      kind: 'success',
      title: `The batch operation "${formatOperationType(batchOperationType)}" has been started`,
      subtitle: 'You can track its progress in the Batch Operation page',
      isDismissable: true,
      isActionable: true,
      actionButtonLabel: 'Go to operation details',
      onActionButtonClick: () => {
        navigate(Paths.batchOperation(batchOperationKey));
      },
    });
  };
}

export {useBatchOperationSuccessNotification};
