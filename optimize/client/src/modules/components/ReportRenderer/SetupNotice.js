/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';

import './SetupNotice.scss';
import {t} from 'translation';

export default function SetupNotice({children, ...props}) {
  return (
    <div className="SetupNotice">
      <h1>{t('report.setupNotice')}</h1>
      <div {...props}>{children}</div>
    </div>
  );
}
