/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Container, InstancesCount} from './styled';

type Props = {
  title: string;
  count?: number;
};

const PanelHeader: React.FC<Props> = ({title, count = 0}) => {
  return (
    <Container role="heading">
      {title}
      {count > 0 && (
        <InstancesCount data-testid="result-count">
          {count} results found
        </InstancesCount>
      )}
    </Container>
  );
};

export {PanelHeader};
