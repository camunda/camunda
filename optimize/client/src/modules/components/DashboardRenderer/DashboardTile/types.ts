/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DashboardTile as TDashboardTile} from 'types';

export type AddonProps = {
  tile: TDashboardTile;
  filter?: unknown[];
  onTileAdd: (tile: TDashboardTile) => void;
  onTileUpdate: (tile: TDashboardTile) => void;
  onTileDelete: (tile: TDashboardTile) => void;
  loadTile: (id: string) => Promise<void>;
  loadTileData?: () => void;
};

export interface DashboardTileProps extends AddonProps {
  disableNameLink?: boolean;
  customizeTileLink?: (id: string) => string;
  addons?: JSX.Element[];
  children?: (props: Partial<AddonProps>) => JSX.Element | null;
}
