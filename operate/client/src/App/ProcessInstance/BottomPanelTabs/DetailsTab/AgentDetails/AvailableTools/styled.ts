/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {orange20, orange70} from '@carbon/colors';

const ToolList = styled.ul`
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-04);

  .tool-category-camunda {
    background-color: ${orange20};
    color: ${orange70};
  }

  .cds--g90 & .tool-category-camunda,
  .cds--g100 & .tool-category-camunda {
    background-color: ${orange70};
    color: ${orange20};
  }

  .cds--tag {
    margin-inline-start: var(--cds-spacing-03);
  }
`;

const ToolName = styled.span`
  font-size: var(--cds-label-01-font-size);
  line-height: var(--cds-label-01-line-height);
  letter-spacing: var(--cds-label-01-letter-spacing);
  font-weight: var(--cds-heading-compact-01-font-weight);
  color: var(--cds-text-primary);
`;

const ToolDescription = styled.p`
  font-size: var(--cds-body-compact-01-font-size);
  line-height: var(--cds-body-compact-01-line-height);
  letter-spacing: var(--cds-body-compact-01-letter-spacing);
  color: var(--cds-text-secondary);
`;

const EmptyHint = styled.span`
  font-size: var(--cds-body-compact-01-font-size);
  line-height: var(--cds-body-compact-01-line-height);
  letter-spacing: var(--cds-body-compact-01-letter-spacing);
  font-weight: var(--cds-body-compact-01-font-weight);
  color: var(--cds-text-secondary);
`;

export {ToolList, ToolName, ToolDescription, EmptyHint};
