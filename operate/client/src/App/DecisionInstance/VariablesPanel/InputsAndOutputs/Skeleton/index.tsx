/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
