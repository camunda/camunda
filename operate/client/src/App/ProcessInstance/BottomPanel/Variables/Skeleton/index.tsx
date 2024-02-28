/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
