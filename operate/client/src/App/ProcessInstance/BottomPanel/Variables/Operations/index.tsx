/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Container, InlineLoading} from './styled';

type Props = {
  className?: string;
  showLoadingIndicator?: boolean;
  children?: React.ReactNode;
};

const Operations: React.FC<Props> = ({
  className,
  showLoadingIndicator,
  children,
}) => {
  return (
    <Container className={className}>
      {showLoadingIndicator && (
        <InlineLoading data-testid="variable-operation-spinner" />
      )}
      {children}
    </Container>
  );
};

export {Operations};
