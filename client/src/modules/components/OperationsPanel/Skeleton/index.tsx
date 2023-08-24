/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
