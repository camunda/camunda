/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DashboardTile, Report} from 'types';

import {TopNNotice} from './TopNNotice';
import {TileFootnote} from './TileFootnote';

interface TileFooterProps {
  tile?: DashboardTile;
  data?: Report;
}

// Renders the top-N notice and the footnote inline on a single row. The tile root is a flex
// column, so the two notices would otherwise stack; wrapping them in one flex item keeps them
// side by side. Each notice still decides on its own whether it renders anything.
export function TileFooter({tile, data}: TileFooterProps) {
  return (
    <div className="tile-footer">
      <TopNNotice tile={tile} data={data} />
      <TileFootnote tile={tile} />
    </div>
  );
}
