/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {Edit} from '@carbon/icons-react';

import {t} from 'translation';
import {DashboardTile} from 'types';

import './EditButton.scss';

interface EditButtonProps {
  tile: DashboardTile;
  onTileUpdate: (tile: DashboardTile) => void;
}

export default function EditButton({tile, onTileUpdate}: EditButtonProps) {
  return (
    <div className="EditButton">
      <Button
        size="sm"
        kind="ghost"
        hasIconOnly
        iconDescription={t('common.edit').toString()}
        renderIcon={Edit}
        tooltipPosition="bottom"
        onClick={() => onTileUpdate(tile)}
      />
    </div>
  );
}
