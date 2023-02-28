/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  Assignee,
  CreationTime,
  SkeletonContainer,
  Name,
  Process,
  Row,
} from './styled';
import {SkeletonText, Stack} from '@carbon/react';

const TaskSkeleton: React.FC = () => {
  return (
    <SkeletonContainer>
      <Stack gap={3}>
        <Row>
          <Name>
            <SkeletonText width="250px" />
          </Name>
          <Process>
            <SkeletonText width="200px" />
          </Process>
        </Row>
        <Row>
          <Assignee>
            <SkeletonText width="50px" />
          </Assignee>
        </Row>
        <Row>
          <CreationTime>
            <SkeletonText width="100px" />
          </CreationTime>
        </Row>
      </Stack>
    </SkeletonContainer>
  );
};

export {TaskSkeleton};
