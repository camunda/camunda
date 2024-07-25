/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode} from 'react';
import classnames from 'classnames';

import {Icon} from 'components';
import {t} from 'translation';

import './NoDataNotice.scss';

interface NoDataNoticeProps {
  type?: string;
  title?: ReactNode;
  children?: ReactNode;
}

export default function NoDataNotice({type, title, children}: NoDataNoticeProps) {
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

function getIconName(type: string) {
  if (type === 'error') {
    return 'warning';
  }
  return type;
}
