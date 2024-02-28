/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, Ul, Li, SkeletonText} from './styled';

type RowProps = {
  columnWidths: string[];
};

const Row: React.FC<RowProps> = ({columnWidths}) => {
  return (
    <Li>
      {columnWidths.map((width, index) => (
        <SkeletonText key={index} width={width} />
      ))}
    </Li>
  );
};

type Props = {
  dataTestId?: string;
  columnWidths: string[];
};
const Skeleton: React.FC<Props> = ({dataTestId, columnWidths}) => {
  return (
    <Container>
      <Ul data-testid={dataTestId}>
        {[...Array(20)].map((_, index) => (
          <Row key={index} columnWidths={columnWidths} />
        ))}
      </Ul>
    </Container>
  );
};

export {Skeleton};
