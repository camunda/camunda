/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
