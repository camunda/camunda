/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getValidVariableValues} from 'modules/utils/filter/getValidVariableValues';

const validateMultipleVariableValues = (values: string) => {
  const parsedValues = getValidVariableValues(values);
  return (
    values === '' || (parsedValues !== undefined && parsedValues.length > 0)
  );
};

export {validateMultipleVariableValues};
