/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Filters} from './Filters';
import {Decision} from './Decision';
import {InstancesTable} from './InstancesTable';
import {Container, RightContainer} from './styled';

const Decisions: React.FC = () => {
  return (
    <Container>
      <Filters />
      <RightContainer>
        <Decision />
        <InstancesTable />
      </RightContainer>
    </Container>
  );
};

export {Decisions};
