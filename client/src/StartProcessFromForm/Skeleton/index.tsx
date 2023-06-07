/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {SkeletonText, TextAreaSkeleton} from '@carbon/react';
import {Container} from './styled';

const Skeleton: React.FC = () => {
  return (
    <Container data-testid="public-form-skeleton" aria-busy="true">
      <SkeletonText heading />
      <TextAreaSkeleton />
      <TextAreaSkeleton />
      <TextAreaSkeleton />
      <TextAreaSkeleton />
    </Container>
  );
};

export {Skeleton};
