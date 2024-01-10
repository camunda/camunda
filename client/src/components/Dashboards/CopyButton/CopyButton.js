/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Copy} from '@carbon/icons-react';
import {Button} from '@carbon/react';
import {useState} from 'react';

import {t} from 'translation';
import {showError} from 'notifications';
import {copyEntity} from 'services';
import {useErrorHandling} from 'hooks';

import './CopyButton.scss';

export default function CopyButton({tile, addTile}) {
  const {mightFail} = useErrorHandling();
  const [loading, setloading] = useState(false);

  function copyTile(tile) {
    if (tile.type === 'optimize_report' && tile.id) {
      setloading(true);
      mightFail(
        copyEntity('report', tile.id),
        (newId) => {
          addTile({
            ...tile,
            position: {x: 0, y: 0},
            id: newId,
          });
        },
        showError,
        () => setloading(false)
      );
    } else {
      addTile({
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
        iconDescription={t('common.copy')}
        renderIcon={Copy}
        tooltipPosition="bottom"
        onClick={() => copyTile(tile)}
        disabled={loading}
      />
    </div>
  );
}
