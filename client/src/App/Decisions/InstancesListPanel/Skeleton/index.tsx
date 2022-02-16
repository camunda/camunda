/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {DecisionContainer, CircleBlock, DecisionBlock} from './styled';
import {TableSkeleton} from 'modules/components/TableSkeleton';

const Skeleton: React.FC = () => {
  return (
    <TableSkeleton
      columns={[
        {
          variant: 'custom',
          customSkeleton: (
            <DecisionContainer>
              <CircleBlock />
              <DecisionBlock />
            </DecisionContainer>
          ),
        },
        {variant: 'block', width: '160px'},
        {variant: 'block', width: '80px'},
        {variant: 'block', width: '140px'},
        {variant: 'block', width: '160px'},
      ]}
    />
  );
};

export {Skeleton};
