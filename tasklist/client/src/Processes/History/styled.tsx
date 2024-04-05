/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import {SkeletonText as BaseSkeletonText} from '@carbon/react';
import styled from 'styled-components';
import {ProcessInstanceStateIcon as BaseProcessInstanceStateIcon} from './ProcessInstanceStateIcon';

const Container = styled.div`
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
`;

const Header = styled.header`
  width: 100%;
  padding: var(--cds-spacing-04) var(--cds-spacing-05);
  border-bottom: 1px solid var(--cds-border-subtle);
`;

const Item = styled.div`
  width: 100%;
  padding: var(--cds-spacing-04) var(--cds-spacing-05);
  border-bottom: 1px solid var(--cds-border-subtle);
  min-height: ${rem(95)};

  &:last-child {
    border-bottom: none;
  }
`;

const SkeletonText = styled(BaseSkeletonText)`
  margin: 0;
`;

const ItemContainer = styled.div`
  width: 100%;
  height: 100%;
  overflow-y: auto;
`;

const Message = styled.div`
  padding: var(--cds-spacing-05);
  border: 1px solid var(--cds-border-subtle);
  background-color: var(--cds-layer);
  margin: var(--cds-spacing-05);
`;

const ProcessInstanceStateIcon = styled(BaseProcessInstanceStateIcon)`
  margin-left: var(--cds-spacing-02);
`;

export {
  Container,
  Header,
  Item,
  SkeletonText,
  ItemContainer,
  Message,
  ProcessInstanceStateIcon,
};
