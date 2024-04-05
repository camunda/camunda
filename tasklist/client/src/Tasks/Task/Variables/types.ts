/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Variable} from 'modules/types';

type FormValues = {
  [key: string]: string;
} & {
  newVariables?: Pick<Variable, 'name' | 'value'>[];
};

export type {FormValues};
