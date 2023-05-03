/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {css, createGlobalStyle} from 'styled-components';
import {CARBON_STYLES} from '@bpmn-io/form-js-carbon-styles';

const FormCustomStyling = createGlobalStyle`
  ${() => css`
    ${CARBON_STYLES}
    .fjs-container {
      .fjs-powered-by,
      .fjs-form-field button[type='submit'] {
        display: none;
      }
    }
  `}
`;

export {FormCustomStyling};
