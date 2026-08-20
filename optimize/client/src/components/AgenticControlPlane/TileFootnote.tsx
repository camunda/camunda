/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';

import type {DashboardTile} from 'types';

interface TileFootnoteConfiguration {
  footnote?: string;
}

interface TileFootnoteProps {
  tile?: DashboardTile;
}

export function TileFootnote({tile}: TileFootnoteProps) {
  const footnoteKey = (tile?.configuration as TileFootnoteConfiguration | undefined)?.footnote;
  if (!footnoteKey) {
    return null;
  }
  return <p className="tile-footnote">{t(footnoteKey)}</p>;
}
