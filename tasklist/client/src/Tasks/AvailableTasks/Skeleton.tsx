/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ListContainer} from './styled';
import {TaskSkeleton} from './Task/TaskSkeleton';

const Skeleton: React.FC = () => {
  return (
    <ListContainer data-testid="tasks-skeleton">
      {Array.from({length: 50}).map((_, index) => (
        <TaskSkeleton key={index} />
      ))}
    </ListContainer>
  );
};

export {Skeleton};
