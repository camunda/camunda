/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const MOCK_TIMESTAMP = '2018-12-12 00:00:00';

function formatDate(
  dateString: Date | string | null,
  placeholder: string | null = '--',
) {
  return dateString ? MOCK_TIMESTAMP : placeholder;
}

export {formatDate, MOCK_TIMESTAMP};
