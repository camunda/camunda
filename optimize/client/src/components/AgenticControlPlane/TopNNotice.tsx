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

export function TopNNotice({tile, data}: Readonly<TopNNoticeProps>) {
  const topN = getTileTopNLimit(tile);
  const total = getResultTotal(data);
  // Only surface the notice when the backend truncated the result to the top N.
  if (topN == null || total == null || total <= topN) {
       return null;
  }
  return <p className="tile-topn-notice">{t('agenticControlPlane.topNNotice', {shown: topN, total})}</p>;
}
