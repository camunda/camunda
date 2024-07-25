/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export function findLetterOption(
  options: HTMLElement[],
  letter: string,
  startIndex: number
): HTMLElement | undefined {
  const found = findOptionFromIndex(options, letter, startIndex);
  if (found) {
    return found;
  } else {
    if (startIndex > 0) {
      return findOptionFromIndex(options, letter, 0);
    }
  }
}

function findOptionFromIndex(
  options: HTMLElement[],
  letter: string,
  startIndex: number
): HTMLElement | undefined {
  return options
    .slice(startIndex)
    .find((el) => el.textContent?.[0]?.toLowerCase() === letter.toLowerCase());
}
