/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {heading01, spacing05, spacing04} from '@carbon/elements';

const Title = styled.h3`
  ${heading01};
  margin-top: ${spacing04};
  margin-bottom: ${spacing05};
`;

const Container = styled.div`
  padding: 0 ${spacing05};
`;

export {Container, Title};
