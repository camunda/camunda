/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
