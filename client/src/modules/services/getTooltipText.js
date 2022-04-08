/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getRelativeValue} from './formatters';

export default function getTooltipText(
  data,
  formatter,
  instanceCount,
  alwaysShowAbsolute,
  alwaysShowRelative,
  hideRelative
) {
  if (!data && data !== 0) {
    return '';
  }

  const absolute = formatter(data);
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
