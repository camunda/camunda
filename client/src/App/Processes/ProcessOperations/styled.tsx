/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {bodyShort01, productiveHeading01} from '@carbon/elements';
import styled from 'styled-components';
import {Anchor} from 'modules/components/Anchor/styled';

const Warning = styled.div`
  padding-top: 4px;
`;
const Information = styled.p`
  ${productiveHeading01};
  margin-top: 0;
`;

const Ul = styled.ul`
  list-style-type: disc;
  list-style-position: inside;
  margin: 0 15px 10px 14px;
  ${bodyShort01};
`;

const Link = styled(Anchor)`
  ${bodyShort01};
`;

export {Warning, Information, Ul, Link};
