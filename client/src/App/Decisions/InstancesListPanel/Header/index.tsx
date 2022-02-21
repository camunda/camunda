/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Container, Title, InstancesCount} from './styled';

type Props = {
  instancesCount: number;
};

const Header: React.FC<Props> = ({instancesCount}) => {
  return (
    <Container role="heading">
      <Title>Instances</Title>
      {instancesCount > 0 && (
        <InstancesCount data-testid="filtered-instances-count">
          {instancesCount} results found
        </InstancesCount>
      )}
    </Container>
  );
};

export {Header};
