/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
