/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {SkeletonText as BaseSkeletonText} from '@carbon/react';

const Container = styled.li`
  padding: var(--cds-spacing-05);
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: var(--cds-spacing-02);
`;

const Details = styled.div`
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  padding-top: var(--cds-spacing-07);
`;

const EntryDetails = styled.div`
  display: flex;
  flex-direction: row;
  justify-content: space-between;
`;

const SkeletonText = styled(BaseSkeletonText)`
  margin: 0;
`;

export {Container, Header, EntryDetails, Details, SkeletonText};
