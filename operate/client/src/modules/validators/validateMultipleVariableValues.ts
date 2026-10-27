/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getValidVariableValues} from 'modules/utils/filter/getValidVariableValues';

const validateMultipleVariableValues = (values: string) => {
  const parsedValues = getValidVariableValues(values);
  return (
    values === '' || (parsedValues !== undefined && parsedValues.length > 0)
  );
};

export {validateMultipleVariableValues};
