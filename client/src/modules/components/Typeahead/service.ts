/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {formatters} from 'services';
import React, {ReactElement, ReactNode} from 'react';
import {Typeahead} from 'components';

export function highlightText(content: ReactNode, query?: string): ReactNode {
  let children: ReactNode;
  if (typeof content === 'string') {
    children = formatters.getHighlightedText(content, query);
  } else {
    children = recursiveMap(content, (child) => {
      if (child.type === Typeahead.Highlight) {
        const {children, matchFromStart} = child.props;
        return formatters.getHighlightedText(children[0], query, matchFromStart);
      }

      return child;
    });
  }

  return children;
}

// https://stackoverflow.com/a/42498730/4016581
function recursiveMap(children: ReactNode, fn: (child: ReactElement) => ReactNode): ReactNode {
  return React.Children.map(children, (child) => {
    if (!React.isValidElement(child)) {
      return child;
    }

    if (child.props.children) {
      child = React.cloneElement(child, {
        children: recursiveMap(child.props.children, fn),
      } as Record<string, ReactNode>);
    }

    return fn(child);
  });
}
