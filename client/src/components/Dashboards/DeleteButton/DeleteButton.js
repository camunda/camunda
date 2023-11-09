/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Button} from '@carbon/react';
import {Close} from '@carbon/icons-react';

import {t} from 'translation';

import './DeleteButton.scss';

export default function DeleteButton({tile, deleteTile}) {
  return (
    <div className="deleteButton">
      <Button
        size="sm"
        kind="ghost"
        hasIconOnly
        iconDescription={t('common.delete')}
        renderIcon={Close}
        tooltipPosition="bottom"
        className="DeleteButton"
        onClick={(event) => deleteTile({event, tile})}
      />
    </div>
  );
}
