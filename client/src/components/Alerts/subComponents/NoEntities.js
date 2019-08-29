/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Link} from 'react-router-dom';

import {Button} from 'components';

import './NoEntities.scss';
import {t} from 'translation';

export default function NoEntities({label, createFunction, link}) {
  const LinkLabel = t(`common.entity.create.${label}`);
  const createLink = link ? (
    <Link to={link} className="createLink">
      {LinkLabel}
    </Link>
  ) : (
    <Button variant="link" className="createLink" onClick={createFunction}>
      {LinkLabel}
    </Button>
  );

  return (
    <li className="NoEntities">
      {t('common.entity.empty', {
        label: t(`${label.toLowerCase()}.label-plural`)
      })}
      {createLink}
    </li>
  );
}
