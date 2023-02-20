/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {Copyright as BaseCopyright} from 'modules/components/Copyright';

const Container = styled.main`
  display: flex;
  width: 100%;
  height: 100%;
`;

const RightContainer = styled.div`
  width: 100%;
  height: 100%;
  margin-right: 56px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
`;

const Copyright = styled(BaseCopyright)`
  text-align: right;
`;

export {Container, RightContainer, Copyright};
