/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as processOptions from './process';
import * as decisionOptions from './decision';
export {updateReport} from './reportConfig';

const config = {
  process: processOptions,
  decision: decisionOptions,
};

export default config;
