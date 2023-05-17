/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
