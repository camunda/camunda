/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/**
 * Adds an 's' at the end of a text, when count is not 1 or -1
 */
export default function pluralSuffix(count: any, text: any) {
  return Math.abs(count) === 1 ? `${count} ${text}` : `${count} ${text}s`;
}
