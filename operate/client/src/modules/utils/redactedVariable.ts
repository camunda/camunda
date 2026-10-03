/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const REDACTED_VALUE_LABEL = '[REDACTED]';

type ProtectableVariable = {
  value: string | null;
  protectionModes?: readonly string[] | null;
};

// the backend exports a redacted value as JSON null, which the API returns as the string "null"
const isRedactedVariable = (variable: ProtectableVariable): boolean =>
  (variable.protectionModes ?? []).includes('REDACT') &&
  (variable.value === null || variable.value === 'null');

export {isRedactedVariable, REDACTED_VALUE_LABEL};
