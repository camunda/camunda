/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, InstancesCount, Title} from './styled';

type Props = {
  title: string;
  count?: number;
  children?: React.ReactNode;
  className?: string;
};

const PanelHeader: React.FC<Props> = ({
  title,
  count = 0,
  children,
  className,
}) => {
  return (
    <Container className={className}>
      <Title>{title}</Title>
      {count > 0 && (
        <InstancesCount data-testid="result-count">
          {count} results found
        </InstancesCount>
      )}
      {children}
    </Container>
  );
};

export {PanelHeader};
