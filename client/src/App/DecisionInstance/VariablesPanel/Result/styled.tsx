/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {StatusMessage} from 'modules/components/StatusMessage';
import styled from 'styled-components';

const Container = styled.div`
  width: 100%;
  height: 100%;
  position: relative;

  & ${StatusMessage} {
    width: 100%;
    height: 58%;
  }
`;

export {Container};
