/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {ReactComponent as Logo} from './logo.svg';

const Main = styled.main`
  width: 100vw;
  height: calc(100vh - 48px);
  overflow-y: auto;
  display: flex;
  justify-content: center;
`;

const LogoIcon = styled(Logo)`
  width: 48px;
  position: absolute;
  left: 0;
  bottom: var(--cds-spacing-04);
  color: var(--cds-text-primary);
`;

const FormContainer = styled.div`
  width: min(100%, 900px);
`;

export {Main, LogoIcon, FormContainer};
