/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

import {styles, rem} from '@carbon/elements';
import {ReactComponent as BaseLogo} from 'modules/components/Icon/logo.svg';
import {Button as BaseButton} from '@carbon/react';

const Button: typeof BaseButton = styled(BaseButton)`
  min-width: 100%;
  margin-top: var(--cds-spacing-05);
`;

const Container = styled.main`
  height: 100%;
  padding: var(--cds-spacing-03);
`;

const CopyrightNotice = styled.span`
  color: var(--cds-text-secondary);
  text-align: center;
  align-self: end;
  ${styles.legal01};
`;

const LogoContainer = styled.div`
  text-align: center;
  padding-top: var(--cds-spacing-12);
  padding-bottom: var(--cds-spacing-02);
`;

const Error = styled.span`
  min-height: calc(${rem(48)} + var(--cds-spacing-06));
  padding-bottom: var(--cds-spacing-06);
`;

const FieldContainer = styled.div`
  min-height: ${rem(84)};
`;

const CamundaLogo = styled(BaseLogo)`
  color: var(--cds-icon-primary);
`;

const Title = styled.h1`
  padding-bottom: var(--cds-spacing-10);
  text-align: center;
  color: var(--cds-text-primary);
  ${styles.productiveHeading05};
`;

export {
  Container,
  CopyrightNotice,
  LogoContainer,
  Error,
  FieldContainer,
  CamundaLogo,
  Title,
  Button,
};
