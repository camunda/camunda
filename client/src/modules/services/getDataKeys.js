/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export default function getDataKeys(data) {
  // We need to do explicit Object coercion thanks to IE
  // eslint-disable-next-line no-new-object
  return Object.keys(new Object(data));
}
