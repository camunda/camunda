/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getRelativeValue} from './formatters';

export default function getTooltipText(
  data,
  formatter,
  instanceCount,
  alwaysShowAbsolute,
  alwaysShowRelative,
  hideRelative,
  precision,
  shortNotation
) {
  if (!data && data !== 0) {
    return '';
  }

  const absolute = formatter(data, precision, shortNotation);
  const relative = getRelativeValue(data, instanceCount);

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
