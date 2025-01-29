/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import type {TaskRisk} from 'modules/types';

type RiskLabel = {
  short: string;
  long: string;
  key: 'veryLow' | 'low' | 'medium' | 'high' | 'urgent';
};

/**
 * Format the risk classification to a human-readable label
 * @param risk - The risk classification
 *
 * @returns The risk label
 */
const getRiskLabel = (
  classification: TaskRisk['classification'],
): RiskLabel => {
  if (classification === 'URGENT') {
    return {
      short: t('taskRiskUrgentShort'),
      long: t('taskRiskUrgentLong'),
      key: 'urgent',
    };
  } else if (classification === 'HIGH') {
    return {
      short: t('taskRiskHighShort'),
      long: t('taskRiskHighLong'),
      key: 'high',
    };
  } else if (classification === 'MEDIUM') {
    return {
      short: t('taskRiskMediumShort'),
      long: t('taskRiskMediumLong'),
      key: 'medium',
    };
  } else if (classification === 'LOW') {
    return {
      short: t('taskRiskLowShort'),
      long: t('taskRiskLowLong'),
      key: 'low',
    };
  } else {
    return {
      short: t('taskRiskVeryLowShort'),
      long: t('taskRiskVeryLowLong'),
      key: 'veryLow',
    };
  }
};

export {getRiskLabel};
