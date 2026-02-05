/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VARIABLE_NAME_DOT_ESCAPE_CHAR} from './constants';

const createVariableFieldName = (name: string) => {
<<<<<<< HEAD:tasklist/client/src/common/tasks/variables-editor/createVariableFieldName.tsx
  const escapedName = name.replaceAll('.', VARIABLE_NAME_DOT_ESCAPE_CHAR);
  return `#${escapedName}`;
=======
  return `#${encodeURIComponent(name).replace(/\./g, '%2E')}`;
>>>>>>> c857b356 (fix: variable form when variables have dots in the name):tasklist/client/src/Tasks/Task/Variables/createVariableFieldName.tsx
};

const createNewVariableFieldName = (prefix: string, suffix: string) => {
  return `${prefix}.${suffix}`;
};

export {createVariableFieldName, createNewVariableFieldName};
