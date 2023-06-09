/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {ReactComponent as Logo} from './logo.svg';
import {Content as BaseContent} from '@carbon/react';

const Content = styled(BaseContent)`
  width: 100vw;
  height: calc(100vh - var(--cds-spacing-09));
  display: grid;
  grid-template-rows: 1fr min-content;
  grid-template-columns: 100%;
  grid-row-gap: var(--cds-spacing-04);
  overflow-y: auto;
  justify-items: center;
`;

const LogoIcon = styled(Logo)`
  width: 48px;
  margin-left: -24px;
  margin-bottom: -24px;
  justify-self: start;
  color: var(--cds-text-primary);
`;

const FormContainer = styled.div`
  width: min(100%, 900px);
`;

export {Content, LogoIcon, FormContainer};
