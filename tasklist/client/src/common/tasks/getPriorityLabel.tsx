/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';

type PriorityLabel = {
  short: string;
  long: string;
  key: 'low' | 'medium' | 'high' | 'critical';
};

/**
 * Format the priority number to a human-readable label
 * @param priority - The priority number
 *
 * @returns The priority label
 */
const getPriorityLabel = (priority: number): PriorityLabel => {
  if (priority > 75) {
    return {
      short: t('taskPriorityCriticalShort'),
      long: t('taskPriorityCriticalLong'),
      key: 'critical',
    };
  } else if (priority > 50) {
    return {
      short: t('taskPriorityHighShort'),
      long: t('taskPriorityHighLong'),
      key: 'high',
    };
  } else if (priority > 25) {
    return {
      short: t('taskPriorityMediumShort'),
      long: t('taskPriorityMediumLong'),
      key: 'medium',
    };
  } else {
    return {
      short: t('taskPriorityLowShort'),
      long: t('taskPriorityLowLong'),
      key: 'low',
    };
  }
};

export {getPriorityLabel};
