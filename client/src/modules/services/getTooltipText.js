/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export default function getTooltipText(
  data,
  formatter,
  processInstanceCount,
  alwaysShowAbsolute,
  alwaysShowRelative,
  hideRelative
) {
  if (!data && data !== 0) {
    return '';
  }

  const absolute = formatter(data);
  const relative = getRelativeValue(data, processInstanceCount);

  if (hideRelative) {
    return absolute;
  }

  if (alwaysShowAbsolute && alwaysShowRelative) {
    return absolute + `\u00A0(${relative})`;
  }

  if (alwaysShowAbsolute) {
    return absolute;
  }

  if (alwaysShowRelative) {
    return relative;
  }

  return absolute + `\u00A0(${relative})`;
}

function getRelativeValue(data, total) {
  if (data === null) {
    return '';
  }
  return Math.round((data / total) * 1000) / 10 + '%';
}
