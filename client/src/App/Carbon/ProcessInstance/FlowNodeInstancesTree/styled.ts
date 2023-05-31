/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {TreeNode as BaseTreeNode} from '@carbon/react';

const TreeNode = styled(BaseTreeNode)`
  .cds--tree-node__label {
    height: 2rem;
  }

  .cds--tree-node__label__details {
    width: 100%;
  }
`;

export {TreeNode};
