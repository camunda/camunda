/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createBatchOperation, createVariable} from 'modules/testUtils';

const mockVariables = [
  createVariable({name: 'mwst', value: '63.27', isFirst: true}),
  createVariable({name: 'orderStatus', value: '"NEW"'}),
  createVariable({name: 'paid', value: 'true'}),
];

const mockVariableOperation = createBatchOperation({type: 'UPDATE_VARIABLE'});

export {mockVariables, mockVariableOperation};
