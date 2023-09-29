/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Button} from '@carbon/react';
import {Edit} from '@carbon/icons-react';

import {t} from 'translation';
import './EditButton.scss';

export default function EditButton({tile, onClick}) {
  return (
    <div className="editButton">
      <Button
        size="sm"
        kind="ghost"
        hasIconOnly
        iconDescription={t('common.edit')}
        renderIcon={Edit}
        tooltipPosition="bottom"
        className="EditButton"
        onClick={() => onClick(tile)}
      />
    </div>
  );
}
