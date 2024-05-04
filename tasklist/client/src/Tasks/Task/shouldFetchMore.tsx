/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

function shouldFetchMore(errorCode: string) {
  const ERROR_CODE_PATTERN =
    /task is not assigned|task is not assigned to|task is not active/gi;

  return ERROR_CODE_PATTERN.test(errorCode);
}

export {shouldFetchMore};
