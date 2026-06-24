/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {notificationsStore} from 'modules/stores/notifications';

const handleMutationError = (options: {
  statusCode: number;
  title: string;
  subtitle?: string;
}) => {
  if (options.statusCode === 403) {
    notificationsStore.displayNotification({
      kind: 'warning',
      title: "You don't have permission to perform this operation",
      subtitle: 'Please contact the administrator if you need access.',
      isDismissable: true,
    });
    return;
  }
  notificationsStore.displayNotification({
    kind: 'error',
    title: options.title,
    subtitle: options.subtitle,
    isDismissable: true,
  });
};

const handleOperationError = (statusCode?: number) => {
  handleMutationError({
    statusCode: statusCode ?? 0,
    title: 'Operation could not be created',
  });
};

const handleBatchOperationError = (statusCode?: number, title?: string) => {
  if (statusCode === 404) {
    notificationsStore.displayNotification({
      kind: 'error',
      title: title ?? 'Operation could not be created',
      subtitle:
        'Batch operation not found. It may have already completed or failed.',
      isDismissable: true,
    });
    return;
  }

  handleMutationError({
    statusCode: statusCode ?? 0,
    title: title ?? 'Operation could not be created',
  });
};

export {handleOperationError, handleBatchOperationError, handleMutationError};
