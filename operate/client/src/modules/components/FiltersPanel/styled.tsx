/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {heading01, spacing05, supportError} from '@carbon/elements';
import {WarningFilled as BaseWarningFilled} from '@carbon/react/icons';

const Title = styled.h3`
  ${heading01};
  padding-bottom: ${spacing05};
`;

const Container = styled.div`
  padding: ${spacing05};
  position: absolute;
  width: 100%;
`;

const WarningFilled = styled(BaseWarningFilled)`
  fill: ${supportError};
`;

const Footer = styled.div`
  display: flex;
  justify-content: center;
  padding: var(--cds-spacing-02);
`;

const ButtonContainer = styled.div`
  position: absolute;
  display: none;
  right: 0;
  top: -8px;
`;

const FieldContainer = styled.div`
  position: relative;
  &:hover {
    ${ButtonContainer} {
      display: block;
    }
  }
`;

const Form = styled.form`
  height: 100%;
`;

export {
  Container,
  Title,
  WarningFilled,
  FieldContainer,
  ButtonContainer,
  Footer,
  Form,
};
