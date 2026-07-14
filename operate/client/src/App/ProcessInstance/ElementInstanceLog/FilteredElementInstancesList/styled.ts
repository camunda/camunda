/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {TreeNode as BaseTreeNode} from '../ElementInstancesTree/styled';

const ScrollContainer = styled.div`
  overflow-y: auto;
  flex: 1;
  min-height: 0;
`;

// Carbon sets padding-inline-start as an inline style based on depth.
// Use !important to override depth-0 (2rem) so the icon lands at the same
// absolute x-position as depth-1 leaf nodes in the instance history tree (3.5rem).
const IndentedTreeNode = styled(BaseTreeNode)`
  .cds--tree-node__label {
    padding-inline-start: 3.5rem !important;
  }
`;

const StatusRegion = styled.output`
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
`;

const EmptyStateContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: var(--cds-spacing-06);
`;

export {ScrollContainer, StatusRegion, EmptyStateContainer, IndentedTreeNode};
