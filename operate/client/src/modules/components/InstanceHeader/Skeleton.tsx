/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Container, Table, Th, Td, SkeletonText, SkeletonIcon} from './styled';

type Props = {
  headerColumns: {name: string; skeletonWidth: string}[];
};

const Skeleton: React.FC<Props> = ({headerColumns}) => {
  return (
    <Container data-testid="instance-header-skeleton">
      <SkeletonIcon />
      <Table>
        <thead>
          <tr>
            {headerColumns.map(({name}, index) => (
              <Th key={index}>{name}</Th>
            ))}
          </tr>
        </thead>
        <tbody>
          <tr>
            {headerColumns.map(({skeletonWidth}, index) => (
              <Td key={index}>
                <SkeletonText width={skeletonWidth} />
              </Td>
            ))}
          </tr>
        </tbody>
      </Table>
      <SkeletonText width={'78px'} />
    </Container>
  );
};

export {Skeleton};
