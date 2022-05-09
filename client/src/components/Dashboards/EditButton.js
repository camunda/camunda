/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Button, Icon} from 'components';

import './EditButton.scss';

export default function EditButton({report, onClick}) {
  return (
    <Button className="EditButton" onClick={() => onClick(report)}>
      <Icon type="edit-small" />
    </Button>
  );
}
