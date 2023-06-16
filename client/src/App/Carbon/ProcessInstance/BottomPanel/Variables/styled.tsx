/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {StructuredList as BaseStructuredList} from 'modules/components/Carbon/StructuredList';

const VariablesContent = styled.div`
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
`;

const Footer = styled.div`
  margin-top: auto;
  border-top: 1px solid var(--cds-border-subtle-01);
`;

const StructuredList = styled(BaseStructuredList)`
  padding: var(--cds-spacing-05);
`;

const EmptyMessageWrapper = styled.div`
  display: flex;
  height: 100%;
  justify-content: center;
  align-items: center;
`;

export {VariablesContent, Footer, StructuredList, EmptyMessageWrapper};
