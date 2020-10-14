/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Button, Icon} from 'components';

import './EditButton.scss';

export default function EditButton({report, onClick}) {
  return (
    <Button className="EditButton" onClick={() => onClick(report)}>
      <Icon type="edit" />
    </Button>
  );
}
