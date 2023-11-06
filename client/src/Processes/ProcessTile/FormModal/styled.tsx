/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {InlineNotification as BaseInlineNotification} from '@carbon/react';

const FormSkeletonContainer = styled.div`
  width: 100%;
  max-width: 900px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  grid-template-rows: repeat(4, min-content);
  grid-gap: var(--cds-spacing-04);
  grid-template-areas:
    'a b'
    'c c'
    'd .'
    'e f';

  & > :nth-child(1) {
    grid-area: a;
  }
  & > :nth-child(2) {
    grid-area: b;
  }
  & > :nth-child(3) {
    grid-area: c;
  }
  & > :nth-child(4) {
    grid-area: d;
  }
  & > :nth-child(5) {
    grid-area: e;
  }
  & > :nth-child(6) {
    grid-area: f;
  }
`;

const InlineNotification = styled(BaseInlineNotification)`
  width: 100%;
`;

const FormContainer = styled.div`
  width: 100%;
  max-height: 100%;
  display: flex;
  justify-content: center;
  flex-direction: column;
  align-items: center;
  overflow-y: hidden;
`;

const FormScrollContainer = styled.div`
  width: 100%;
  max-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
`;

const InlineErrorContainer = styled.div`
  padding-top: var(--cds-spacing-02);
`;

export {
  FormSkeletonContainer,
  InlineNotification,
  FormContainer,
  FormScrollContainer,
  InlineErrorContainer,
};
