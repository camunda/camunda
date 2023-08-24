/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const SETUP_WAITING_TIME = 20000;
const SETUP_WAITING_TIME_LONG = 40000;
const DATE_REGEX = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/;
const DEFAULT_TEST_TIMEOUT = 30 * 1000;

export {
  SETUP_WAITING_TIME,
  SETUP_WAITING_TIME_LONG,
  DATE_REGEX,
  DEFAULT_TEST_TIMEOUT,
};
