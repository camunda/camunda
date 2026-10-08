/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Children, Fragment, ReactElement, ReactNode} from 'react';

export default function ignoreFragments<P = unknown>(children?: ReactNode): ReactElement<P>[] {
  if (isFragment(children)) {
    return ignoreFragments<P>(children.props.children);
  }

  return Children.toArray(children)
    .filter(isReactElement<P>)
    .reduce<ReactElement<P>[]>((arr, child) => {
      if (isFragment(child)) {
        return arr.concat(...ignoreFragments<P>(child.props.children));
      }
      return [...arr, child];
    }, []);
}

function isFragment(child: ReactNode): child is ReactElement<{children?: ReactNode}> {
  return isReactElement(child) && child.type === Fragment;
}

function isReactElement<P = unknown>(child: ReactNode): child is ReactElement<P> {
  return !!child && typeof child === 'object' && 'type' in child;
}
