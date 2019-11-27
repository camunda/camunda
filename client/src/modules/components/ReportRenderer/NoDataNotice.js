/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import './NoDataNotice.scss';
import {t} from 'translation';

export default function NoDataNotice({title, children}) {
  return (
    <div className="NoDataNotice">
      <h1>{title || t('report.noDataNotice')}</h1>
      <p>{children}</p>
    </div>
  );
}
