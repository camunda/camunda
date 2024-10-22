/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode} from 'react';
import classnames from 'classnames';
import {Information, Misuse, Warning} from '@carbon/icons-react';

import {t} from 'translation';

import './NoDataNotice.scss';

interface NoDataNoticeProps {
  type?: 'error' | 'warning' | 'info';
  title?: ReactNode;
  children?: ReactNode;
}

export default function NoDataNotice({type, title, children}: NoDataNoticeProps) {
  const Icon = getIcon(type);
  return (
    <div className={classnames('NoDataNotice', type)}>
      <div className="container">
        <div className="title">
          {Icon && <Icon size="20" className={type} />}
          <h1>{title || t('report.noDataNotice')}</h1>
        </div>
        <p>{children}</p>
      </div>
    </div>
  );
}

function getIcon(type: string | undefined) {
  switch (type) {
    case 'error':
      return Misuse;
    case 'warning':
      return Warning;
    case 'info':
      return Information;
    default:
      return null;
  }
}
