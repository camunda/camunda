/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Button} from '@carbon/react';
import {StructuredList as BaseStructuredList} from 'modules/components/StructuredList';
import {Content as BaseContent} from './styled';
import {Layer as BaseLayer} from '@carbon/react';

const Content = styled(BaseContent)`
  display: flex;
  flex-direction: column;
  height: 100%;
`;

const StructuredList = styled(BaseStructuredList)`
  padding: var(--cds-spacing-05);
  [role='table'] {
    table-layout: fixed;
  }
`;

const EmptyMessageWrapper = styled.div`
  display: flex;
  height: 100%;
  justify-content: center;
  align-items: center;
  text-align: center;

  > div {
    max-width: 475px;
  }
`;

const FooterContainer = styled.div`
  margin-top: auto;
  border-top: 1px solid var(--cds-border-subtle-01);
`;

const FooterLayer = styled(BaseLayer)`
  width: 100%;
  display: flex;
  gap: var(--cds-spacing-05);
`;

const ViewMetadataButton = styled(Button)`
  align-self: flex-start;
`;

export {
  Content,
  StructuredList,
  EmptyMessageWrapper,
  FooterContainer,
  FooterLayer,
  ViewMetadataButton,
};
