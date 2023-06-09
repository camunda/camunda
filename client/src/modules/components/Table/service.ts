/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {CSSProperties} from 'react';
import {Head} from './Table';

type Entry = Head;
export function flatten(ctx: string = '', suffix: (entry: Entry) => string | undefined = () => '') {
  return (flat: string[], entry: Entry): string[] => {
    if (typeof entry === 'object' && entry.columns) {
      // nested column, flatten recursivly with augmented context
      return flat.concat(
        entry.columns.reduce<string[]>(flatten(ctx + (entry.id || entry.label), suffix), [])
      );
    } else {
      // normal column, return current context with optional suffix
      return flat.concat(ctx + suffix(entry));
    }
  };
}

// We have to do this, because when header is sortable, style props are only passed to the button that is rendered inside the header
// see implementation here: https://github.com/carbon-design-system/carbon/blob/main/packages/react/src/components/DataTable/TableHeader.tsx#L179
export function rewriteHeaderStyles(styles?: CSSProperties) {
  return function (th: HTMLTableCellElement | null) {
    if (!th) {
      return;
    }

    styles &&
      Object.entries(styles).forEach(([key, value]) => {
        // @ts-ignore
        th.style[key] = value;
      });
  };
}
