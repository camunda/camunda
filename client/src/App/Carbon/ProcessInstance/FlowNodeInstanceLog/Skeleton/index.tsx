/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {SkeletonIcon} from '@carbon/react';
import {Row, SkeletonText} from './styled';

const TreeNode: React.FC = () => {
  return (
    <Row>
      <SkeletonIcon />
      <SkeletonText width="143px" />
    </Row>
  );
};

const Skeleton: React.FC = () => {
  return (
    <ul data-testid="skeleton">
      {[...Array(10)].map((_, index) => (
        <TreeNode key={index} />
      ))}
    </ul>
  );
};

export {Skeleton};
