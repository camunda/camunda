/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type IdCriterion = {$in?: string[]; $notIn?: string[]};

const buildProcessInstanceKeyCriterion = (
  includeIds: string[] = [],
  excludeIds: string[] = [],
): IdCriterion | undefined => {
  const criterion: IdCriterion = {};
  if (includeIds.length > 0) {
    criterion.$in = includeIds;
  }
  if (excludeIds.length > 0) {
    criterion.$notIn = excludeIds;
  }
  return Object.keys(criterion).length > 0 ? criterion : undefined;
};

export {buildProcessInstanceKeyCriterion};
