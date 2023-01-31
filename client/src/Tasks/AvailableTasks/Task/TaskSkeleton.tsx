/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Assignee, CreationTime, SkeletonLi, Name, Process, Row} from './styled';
import {SkeletonText, Stack} from '@carbon/react';

const TaskSkeleton: React.FC = () => {
  return (
    <SkeletonLi>
      <Stack gap={5}>
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
          <CreationTime>
            <SkeletonText width="100px" />
          </CreationTime>
        </Row>
      </Stack>
    </SkeletonLi>
  );
};

export {TaskSkeleton};
