/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Item, SkeletonText} from './styled';
import {Stack} from '@carbon/react';

const Skeleton: React.FC = () => {
  return (
    <div data-testid="history-skeleton">
      {Array.from({length: 50}).map((_, index) => (
        <Stack key={index} gap={3} as={Item}>
          <SkeletonText width="225px" />
          <SkeletonText width="175px" />
          <SkeletonText width="200px" />
        </Stack>
      ))}
    </div>
  );
};

export {Skeleton};
