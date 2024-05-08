/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
    <ul data-testid="instance-history-skeleton">
      {[...Array(10)].map((_, index) => (
        <TreeNode key={index} />
      ))}
    </ul>
  );
};

export {Skeleton};
