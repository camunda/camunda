/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Link} from 'react-router-dom';

import {Button} from 'components';

import './NoEntities.scss';

export default function NoEntities({label, createFunction, link}) {
  const createLink = link ? (
    <Link to={link} className="createLink">
      Create a new {label}…
    </Link>
  ) : (
    <Button variant="link" className="createLink" onClick={createFunction}>
      Create a new {label}…
    </Button>
  );

  return (
    <li className="NoEntities">
      There are no {label}s configured.
      {createLink}
    </li>
  );
}
