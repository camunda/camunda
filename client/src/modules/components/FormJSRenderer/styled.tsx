/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, createGlobalStyle} from 'styled-components';
import {CARBON_STYLES} from '@bpmn-io/form-js-carbon-styles';
import {Layer as BaseLayer} from '@carbon/react';

const FormRoot = styled.div`
  width: 100%;
`;

const FormJSCustomStyling = createGlobalStyle`
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

const Layer = styled(BaseLayer)`
  width: 100%;
  max-width: 900px;
`;

export {FormRoot, FormJSCustomStyling, Layer};
