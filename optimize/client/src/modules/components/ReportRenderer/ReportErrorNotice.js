/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';

import {t} from 'translation';

import NoDataNotice from './NoDataNotice';

export default function ReportErrorNotice({error}) {
  const formattedError = formatError(error);

  return (
    <NoDataNotice type={formattedError.type} title={formattedError.title}>
      {formattedError.message}
    </NoDataNotice>
  );
}

function formatError({
  status,
  message = t('apiErrors.reportEvaluationError'),
  title = t('report.errorNotice'),
}) {
  if (status === 403) {
    return {
      type: 'info',
      title: t('dashboard.noAuthorization'),
      text: t('dashboard.noReportAccess'),
    };
  }

  return {
    type: 'error',
    title,
    message,
  };
}
