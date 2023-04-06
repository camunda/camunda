/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
