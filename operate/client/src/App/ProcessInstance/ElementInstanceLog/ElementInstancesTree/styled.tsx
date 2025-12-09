/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {INSTANCE_HISTORY_LEFT_PADDING} from 'modules/constants';
import styled, {css} from 'styled-components';
import {ElementInstanceIcon as BaseElementInstanceIcon} from 'modules/components/ElementInstanceIcon';
import {TreeNode as BaseTreeNode} from '@carbon/react';

const ElementInstanceIcon = styled(BaseElementInstanceIcon)<{
  $hasLeftMargin: boolean;
}>`
  ${({$hasLeftMargin}) => {
    return css`
      ${$hasLeftMargin
        ? css`
            margin-left: ${INSTANCE_HISTORY_LEFT_PADDING};
          `
        : ''}
    `;
  }}
`;
const TreeNode = styled(BaseTreeNode)`
  .cds--tree-node__label {
    height: 2rem;

    .cds--tree-parent-node__toggle {
      margin-left: ${INSTANCE_HISTORY_LEFT_PADDING};
    }
  }

  .cds--tree-node__label__details {
    width: 100%;
  }
`;

const NodeContainer = styled.div`
  position: absolute;
  width: 100%;
`;

const InstanceHistory = styled.div`
  position: relative;
  height: 100%;
  overflow: auto;
`;

export {ElementInstanceIcon, NodeContainer, InstanceHistory, TreeNode};
