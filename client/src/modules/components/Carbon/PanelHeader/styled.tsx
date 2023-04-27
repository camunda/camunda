/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {layer01, borderSubtle01, spacing04, spacing09} from '@carbon/elements';

const Header = styled.header`
  background-color: ${layer01};
  border-bottom: solid 1px ${borderSubtle01};
  padding: ${spacing04};
  display: flex;
  align-items: center;
  min-height: ${spacing09};
`;

export {Header};
