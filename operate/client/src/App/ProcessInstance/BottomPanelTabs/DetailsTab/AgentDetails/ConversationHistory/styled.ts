/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import styled from 'styled-components';

const ConversationContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-04);

  .cds--btn-set > .cds--btn {
    inline-size: auto;
  }
`;

const Messages = styled.div`
  display: flex;
  flex-direction: inherit;
  gap: inherit;
  transition: opacity 150ms ease;

  &[data-dimmed='true'] {
    opacity: 0.5;
    pointer-events: none;
  }
`;

const StatusHint = styled.span`
  font-size: var(--cds-body-compact-01-font-size);
  font-weight: var(--cds-body-compact-01-font-weight);
  line-height: var(--cds-body-compact-01-line-height);
  letter-spacing: var(--cds-body-compact-01-letter-spacing);
  text-align: center;
  color: var(--cds-text-secondary);
`;

const ShowMoreButton = styled(Button)`
  align-self: center;
`;

export {ConversationContainer, Messages, StatusHint, ShowMoreButton};
