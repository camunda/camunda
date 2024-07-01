/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
