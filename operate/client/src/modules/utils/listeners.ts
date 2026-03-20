/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const GLOBAL_LISTENER_TAG = 'GLOBAL_LISTENER';

function isGlobalListener(tags: string[]): boolean {
  return tags.includes(GLOBAL_LISTENER_TAG);
}

export {GLOBAL_LISTENER_TAG, isGlobalListener};
