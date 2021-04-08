/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';

import NoDataNotice from './NoDataNotice';

export default function ReportErrorNotice({error}) {
  const formattedError = formatError(error);

  return (
    <NoDataNotice type={formattedError.type} title={formattedError.title}>
      {formattedError.text}
    </NoDataNotice>
  );
}

function formatError({status, data: {errorCode, errorMessage}}) {
  if (status === 403) {
    return {
      type: 'info',
      title: t('dashboard.noAuthorization'),
      text: t('dashboard.noReportAccess'),
    };
  }

  return {
    type: 'error',
    title: t('report.errorNotice'),
    text: errorCode ? t('apiErrors.' + errorCode) : errorMessage,
  };
}
