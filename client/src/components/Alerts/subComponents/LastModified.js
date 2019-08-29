/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import './LastModified.scss';
import {t} from 'translation';

export default function LastModified({label, date, author}) {
  return (
    <span className="LastModified">
      {label} {moment(date).format('lll')}
      <br />
      {t('common.entity.by')} <strong>{author}</strong>
    </span>
  );
}
