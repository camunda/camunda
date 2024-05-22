/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

function formatError({status, message}) {
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
    message: message || t('apiErrors.reportEvaluationError'),
  };
}
