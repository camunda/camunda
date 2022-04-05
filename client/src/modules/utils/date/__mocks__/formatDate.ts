/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const MOCK_TIMESTAMP = '2018-12-12 00:00:00';

function formatDate(
  dateString: Date | string | null,
  placeholder: string | null = '--'
) {
  return dateString ? MOCK_TIMESTAMP : placeholder;
}

export {formatDate, MOCK_TIMESTAMP};
