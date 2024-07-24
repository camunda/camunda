/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {InlineLoadingStatus} from '@carbon/react';
import {useTranslation} from 'react-i18next';

function getCompletionButtonDescription(status: InlineLoadingStatus) {
  
  const {t} = useTranslation();
  
  if (status === 'active') {
    return t('completingTask');
  }

  if (status === 'error') {
    return t('completionFailed');
  }

  if (status === 'finished') {
    return t('completed');
  }

  return undefined;
}

export {getCompletionButtonDescription};
