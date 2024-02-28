/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
