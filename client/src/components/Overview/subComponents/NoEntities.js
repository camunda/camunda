/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Button} from 'components';

import './NoEntities.scss';

export default function NoEntities({label, createFunction}) {
  return (
    <li className="NoEntities">
      There are no {label}s configured.
      <Button variant="link" className="createLink" onClick={createFunction}>
        Create a new {label}…
      </Button>
    </li>
  );
}
