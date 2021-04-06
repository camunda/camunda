/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Icon} from 'components';
import {t} from 'translation';

import './NoDataNotice.scss';

export default function NoDataNotice({type, title, children}) {
  return (
    <div className={classnames('NoDataNotice', type)}>
      <div className="container">
        <h1>
          {type && <Icon size="20" type={getIconName(type) + '-outline'} />}
          {title || t('report.noDataNotice')}
        </h1>
        <p>{children}</p>
      </div>
    </div>
  );
}

function getIconName(type) {
  if (type === 'error') {
    return 'warning';
  }
  return type;
}
