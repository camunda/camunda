/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type VariableFormValues = {
<<<<<<< HEAD
  [key: string]: string;
} & {
=======
  name: string;
  value: string;
  variableKey?: string;
>>>>>>> dbca852b (fix: truncated variables create/update stuck in loading state)
  newVariables?: {id: string; name: string; value: string}[];
};

export type {VariableFormValues};
