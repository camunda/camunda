/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Label, SkeletonContainer, Row} from './styled';
import {SkeletonText, Stack} from '@carbon/react';
import {BodyCompact} from 'modules/components/FontTokens';

const TaskSkeleton: React.FC = () => {
  return (
    <SkeletonContainer>
      <Stack gap={3}>
        <Row>
          <BodyCompact>
            <SkeletonText width="250px" />
          </BodyCompact>
          <Label $variant="secondary">
            <SkeletonText width="200px" />
          </Label>
        </Row>
        <Row>
          <Label $variant="secondary">
            <SkeletonText width="50px" />
          </Label>
        </Row>
        <Row>
          <Label $variant="secondary">
            <SkeletonText width="100px" />
          </Label>
        </Row>
      </Stack>
    </SkeletonContainer>
  );
};

export {TaskSkeleton};
