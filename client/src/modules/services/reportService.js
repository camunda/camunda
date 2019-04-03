/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function isDurationReport(report) {
  // waiting for optional chaining... https://github.com/tc39/proposal-optional-chaining
  return (
    report &&
    report.data &&
    report.data.view &&
    report.data.view.property &&
    report.data.view.property.toLowerCase().includes('duration')
  );
}
