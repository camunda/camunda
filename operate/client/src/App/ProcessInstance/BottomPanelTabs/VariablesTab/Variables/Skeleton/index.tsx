/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Ul, Li, SkeletonText} from './styled';

const VariableRow: React.FC = () => {
  return (
    <Li>
      <SkeletonText width="276px" />
      <SkeletonText width="384px" />
    </Li>
  );
};

const Skeleton: React.FC = () => {
  return (
    <Ul data-testid="variables-skeleton">
      {[...Array(20)].map((_, index) => (
        <VariableRow key={index} />
      ))}
    </Ul>
  );
};

export {Skeleton};
