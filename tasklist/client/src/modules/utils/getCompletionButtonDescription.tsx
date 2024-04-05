/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {InlineLoadingStatus} from '@carbon/react';

function getCompletionButtonDescription(status: InlineLoadingStatus) {
  if (status === 'active') {
    return 'Completing task...';
  }

  if (status === 'error') {
    return 'Completion failed';
  }

  if (status === 'finished') {
    return 'Completed';
  }

  return undefined;
}

export {getCompletionButtonDescription};
