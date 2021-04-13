/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {t} from 'translation';

export default async function parseError(error, fallback = 'Unknown error') {
  let parsedProps = {message: error.message || fallback};

  if (typeof error.json === 'function') {
    try {
      const {errorCode, errorMessage, ...errorProps} = await error.json();
      parsedProps = {
        message: errorCode ? t('apiErrors.' + errorCode) : errorMessage,
        ...errorProps,
      };
    } catch (e) {
      // We should show an error, but cannot parse the error
      // e.g. the server did not return the expected error object
      console.error('Tried to parse error object, but failed', error);
      return;
    }
  }

  return {status: error.status, ...parsedProps};
}
