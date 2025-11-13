/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {notificationsStore} from 'modules/stores/notifications';

const handleOperationError = (isPermissionError: boolean) => {
  if (isPermissionError) {
    return notificationsStore.displayNotification({
      kind: 'warning',
      title: "You don't have permission to perform this operation",
      subtitle: 'Please contact the administrator if you need access.',
      isDismissable: true,
    });
  }
  notificationsStore.displayNotification({
    kind: 'error',
    title: 'Operation could not be created',
    isDismissable: true,
  });
};

export {handleOperationError};
