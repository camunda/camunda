/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Copy} from '@carbon/icons-react';
import {Button} from '@carbon/react';
import {useState} from 'react';

import {t} from 'translation';
import {showError} from 'notifications';
import {copyEntity} from 'services';
import {useErrorHandling} from 'hooks';
import {DashboardTile} from 'types';

import './CopyButton.scss';

interface CopyButtonProps {
  tile: DashboardTile;
  onTileAdd: (tile: DashboardTile) => void;
}

export default function CopyButton({tile, onTileAdd}: CopyButtonProps) {
  const {mightFail} = useErrorHandling();
  const [loading, setloading] = useState(false);

  function copyTile(tile: DashboardTile) {
    if (tile.type === 'optimize_report' && tile.id) {
      setloading(true);
      mightFail(
        copyEntity('report', tile.id),
        (newId) => {
          onTileAdd({
            ...tile,
            position: {x: 0, y: 0},
            id: newId,
          });
        },
        showError,
        () => setloading(false)
      );
    } else {
      onTileAdd({
        ...tile,
        position: {x: 0, y: 0},
      });
    }
  }

  return (
    <div className="CopyButton">
      <Button
        size="sm"
        kind="ghost"
        hasIconOnly
        iconDescription={t('common.copy').toString()}
        renderIcon={Copy}
        tooltipPosition="bottom"
        onClick={() => copyTile(tile)}
        disabled={loading}
      />
    </div>
  );
}
