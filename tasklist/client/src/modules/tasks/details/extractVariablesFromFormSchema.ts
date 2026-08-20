/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getSchemaVariables} from '@bpmn-io/form-js-viewer';

function extractVariablesFromFormSchema(
  schema: string | null | undefined,
): string[] {
  if (schema === null || schema === undefined) {
    return [];
  }

  try {
    return getSchemaVariables(JSON.parse(schema ?? '{}'));
  } catch {
    return [];
  }
}

export {extractVariablesFromFormSchema};
