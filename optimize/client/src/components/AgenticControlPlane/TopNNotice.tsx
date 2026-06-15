/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';

import type {DashboardTile, Report} from 'types';

import {getResultTotal, getTileTopNLimit} from './tilePagination';

interface TopNNoticeProps {
  tile?: DashboardTile;
  data?: Report;
}

export function TopNNotice({tile, data}: TopNNoticeProps) {
  const topN = getTileTopNLimit(tile);
  if (topN == null) {
    return null;
  }

  const total = getResultTotal(data);
  if (total == null) {
    return null;
  }

  const shown = Math.min(topN, total);
  if (total <= shown) {
    return null;
  }

  return <p className="tile-topn-notice">{t('agenticControlPlane.topNNotice', {shown, total})}</p>;
}
