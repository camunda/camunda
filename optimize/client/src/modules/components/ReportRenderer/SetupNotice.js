/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
