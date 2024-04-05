/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {Content as BaseContent} from '@carbon/react';

const Content = styled(BaseContent)`
  width: 100vw;
  height: 100vh;
  display: flex;
  justify-content: center;
  overflow-y: auto;
`;

const FormContainer = styled.div`
  width: min(100%, 900px);
  height: min-content;
`;

export {Content, FormContainer};
