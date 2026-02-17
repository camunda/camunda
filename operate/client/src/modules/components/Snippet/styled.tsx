/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CodeSnippet} from '@carbon/react';
import {colors} from '@carbon/elements';
import styled from 'styled-components';

const BaseSnippet = styled(CodeSnippet)`
  border-radius: 4px;
  block-size: auto;
  && {
  padding: var(--cds-spacing-02);
    }
`;

const LightSnippet = styled(BaseSnippet)`
  --cds-layer: ${colors.gray[60]};
`;

const DarkSnippet = styled(BaseSnippet)`
  --cds-layer: ${colors.black[100]};
`;

export {LightSnippet, DarkSnippet}
