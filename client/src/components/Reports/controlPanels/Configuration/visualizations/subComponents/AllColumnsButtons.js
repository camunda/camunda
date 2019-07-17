/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ButtonGroup, Button} from 'components';
import './AllColumnsButtons.scss';

export default function AllColumnsButtons({enableAll, disableAll}) {
  return (
    <ButtonGroup className="AllColumnsButtons">
      <Button onClick={enableAll}>Enable All</Button>
      <Button onClick={disableAll}>Disable All</Button>
    </ButtonGroup>
  );
}
