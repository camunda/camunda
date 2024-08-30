/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {Close} from '@carbon/icons-react';

import {t} from 'translation';
import {DashboardTile} from 'types';

import './DeleteButton.scss';

interface DeleteButtonProps {
  tile: DashboardTile;
  onTileDelete: (tile: DashboardTile) => void;
}

export default function DeleteButton({tile, onTileDelete}: DeleteButtonProps) {
  return (
    <div className="DeleteButton">
      <Button
        size="sm"
        kind="ghost"
        hasIconOnly
        iconDescription={t('common.delete').toString()}
        renderIcon={Close}
        tooltipPosition="bottom"
        onClick={() => onTileDelete(tile)}
      />
    </div>
  );
}
