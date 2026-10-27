/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const TRUNCATION_LIMIT = 60;

function middleTruncate(
  text: string,
  limit: number = TRUNCATION_LIMIT,
): string {
  if (text.length <= limit) {
    return text;
  }
  const half = Math.floor((limit - 1) / 2);
  return text.slice(0, half) + '…' + text.slice(text.length - half);
}

export {middleTruncate, TRUNCATION_LIMIT};
