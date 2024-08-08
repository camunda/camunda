/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {tracking} from 'modules/tracking';

async function requestPermission() {
  const permission = await Notification.requestPermission();

  tracking.track({
    eventName: 'os-notification-permission-requested',
    outcome: permission,
  });

  return permission;
}

export {requestPermission};
