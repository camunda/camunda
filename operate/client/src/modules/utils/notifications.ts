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
    return notificationsStore.displayNotification({
      kind: 'warning',
      title: "You don't have permission to perform this operation",
      subtitle: 'Please contact the administrator if you need access.',
      isDismissable: true,
    });
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

export {handleOperationError, handleMutationError};
