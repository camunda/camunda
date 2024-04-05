/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {tracking} from 'modules/tracking';

async function requestPermission() {
  const permission = await Notification.requestPermission();

  tracking.track({
    eventName: 'os-notification-permission-requested',
    outcome: permission,
  });
}

export {requestPermission};
