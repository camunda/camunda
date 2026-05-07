/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {
  TreeNode as ShadcnTreeNode,
  TreeView as ShadcnTreeView,
} from './tree-view.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {
  TreeNodeProps as CarbonTreeNodeProps,
  TreeViewProps as CarbonTreeViewProps,
} from '@carbon/react';

export type TreeViewProps = CarbonTreeViewProps;
export type TreeNodeProps = CarbonTreeNodeProps;

type ShadcnTreeViewProps = React.ComponentProps<typeof ShadcnTreeView>;
type ShadcnTreeNodeProps = React.ComponentProps<typeof ShadcnTreeNode>;

function TreeView(props: TreeViewProps) {
  // Carbon's onSelect signature differs (controlled vs uncontrolled). Cast
  // through to the primitive's narrower controlled signature.
  return (
    <ShadcnTreeView {...(props as unknown as ShadcnTreeViewProps)} />
  );
}

function TreeNode(props: TreeNodeProps) {
  const {
    active,
    align,
    autoAlign,
    depth,
    onNodeFocusEvent,
    onTreeSelect,
    selected,
    id,
    label,
    ...rest
  } = props;

  warnDroppedProps('TreeNode', {
    active,
    align,
    autoAlign,
    depth,
    onNodeFocusEvent,
    onTreeSelect,
    selected,
  });

  // Carbon's TreeNode treats `id` as optional, but the shadcn primitive
  // requires a unique id. Fall back to a generated one when callers omit it.
  const fallbackId = React.useId();
  const resolvedId = id ?? fallbackId;

  return (
    <ShadcnTreeNode
      id={resolvedId}
      label={label}
      {...(rest as Omit<ShadcnTreeNodeProps, 'id' | 'label'>)}
    />
  );
}

export {TreeNode, TreeView};
