/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import {t} from 'translation';
import './ModificationInfo.scss';

export default function ModificationInfo({date, user}) {
  return (
    <span className="ModificationInfo">
      {t('common.entity.modified')} {moment(date).format('lll')} {t('common.entity.by')} {user}{' '}
    </span>
  );
}
