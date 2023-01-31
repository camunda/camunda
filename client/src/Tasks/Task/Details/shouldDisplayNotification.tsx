/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

function shouldDisplayNotification(errorCode: string) {
  const ERROR_CODE_PATTERN = /task is not assigned/gi;

  return !ERROR_CODE_PATTERN.test(errorCode);
}

export {shouldDisplayNotification};
