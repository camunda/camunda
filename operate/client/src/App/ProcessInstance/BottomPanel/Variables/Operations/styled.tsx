/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {InlineLoading as BaseInlineLoading} from '@carbon/react';

type ContainerProps = {
  className?: string;
};

const Container = styled.div<ContainerProps>`
  display: flex;
  justify-content: end;
  min-width: var(--cds-spacing-10);

  .cds--tooltip-content {
    text-overflow: ellipsis;
    overflow: hidden;
    white-space: nowrap;
  }
`;

const InlineLoading = styled(BaseInlineLoading)`
  justify-content: end;
`;

export {Container, InlineLoading};
