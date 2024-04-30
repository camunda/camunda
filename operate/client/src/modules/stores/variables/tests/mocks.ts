/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createBatchOperation, createVariable} from 'modules/testUtils';

const mockVariables = [
  createVariable({name: 'mwst', value: '63.27', isFirst: true}),
  createVariable({name: 'orderStatus', value: '"NEW"'}),
  createVariable({name: 'paid', value: 'true'}),
];

const mockVariableOperation = createBatchOperation({type: 'UPDATE_VARIABLE'});

export {mockVariables, mockVariableOperation};
