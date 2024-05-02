/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SkeletonIcon} from '@carbon/react';
import {Container, Header, Details, SkeletonText} from './styled';

const OperationEntry: React.FC = () => {
  return (
    <Container>
      <Header>
        <SkeletonText width={'45px'} />
        <SkeletonIcon />
      </Header>
      <SkeletonText width={'279px'} />

      <Details>
        <SkeletonText width={'67px'} />
        <SkeletonText width={'143px'} />
      </Details>
    </Container>
  );
};

const Skeleton: React.FC = () => {
  return (
    <ul data-testid="skeleton">
      {[...Array(10)].map((_, index) => (
        <OperationEntry key={index} />
      ))}
    </ul>
  );
};

export {Skeleton};
