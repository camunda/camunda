/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Layer} from '@carbon/react';

const CodeSnippetContainer = styled(Layer)`
  display: inline-flex;
  margin-left: var(--cds-spacing-09);
  position: relative;
  &:before {
    content: ' ';
    position: absolute;
    left: calc(-1 * var(--cds-spacing-05));
    height: var(--cds-spacing-06);
    width: 1px;
    background-color: var(--cds-border-subtle-01);
  }
`;

export {CodeSnippetContainer};
