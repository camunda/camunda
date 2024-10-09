/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type InlineLoadingProps} from '@carbon/react';
import {t} from 'i18next';

function getCompletionButtonDescription(
  status: NonNullable<InlineLoadingProps['status']>,
) {
  if (status === 'active') {
    return t('taskDetailsCompletingTaskMessage');
  }

  if (status === 'error') {
    return t('taskDetailsCompletionFailedMessage');
  }

  if (status === 'finished') {
    return t('taskDetailsCompletedTaskMessage');
  }

  return undefined;
}

export {getCompletionButtonDescription};
