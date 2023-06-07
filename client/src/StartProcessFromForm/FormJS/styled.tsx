/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, createGlobalStyle} from 'styled-components';
import {CARBON_STYLES} from '@bpmn-io/form-js-carbon-styles';
import {Heading as BaseHeading} from '@carbon/react';

const FormCustomStyling = createGlobalStyle`
  ${() => css`
    ${CARBON_STYLES}
    .fjs-container {
      .fjs-form-field button[type='submit'],
      .fjs-powered-by {
        display: none;
      }
    }
  `}
`;

const FormRoot = styled.div`
  width: 100%;
`;

const FormContainer = styled.div`
  width: 100%;
  background-color: var(--cds-layer);
  padding: var(--cds-spacing-08) var(--cds-spacing-06);
`;

const Container = styled.div`
  width: 100%;
`;

const Heading = styled(BaseHeading)`
  word-break: break-word;
`;

const SubmitButtonRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--cds-spacing-04) var(--cds-spacing-06);
`;

export {
  FormCustomStyling,
  FormRoot,
  Container,
  FormContainer,
  Heading,
  SubmitButtonRow,
};
