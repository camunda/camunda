/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode} from 'react';
import {Link} from 'react-router-dom';

import {t} from 'translation';

import './ErrorPage.scss';

interface ErrorPageProps {
  children?: ReactNode;
  noLink?: boolean;
  text?: string;
}

export default function ErrorPage({children, noLink, text}: ErrorPageProps) {
  return (
    <div className="ErrorPage">
      <h1>{text || t('common.errors.inValidLink')}</h1>
      {!noLink && (
        <Link className="cds--link" to="/">
          {t('common.goToHome')}…
        </Link>
      )}
      {children}
    </div>
  );
}
