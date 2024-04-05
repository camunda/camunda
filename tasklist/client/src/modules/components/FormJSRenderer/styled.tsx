/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, createGlobalStyle} from 'styled-components';
import {Layer as BaseLayer} from '@carbon/react';
import {Warning as BaseWarning} from '@carbon/react/icons';
import {breakpoints} from '@carbon/elements';

const FormRoot = styled.div`
  width: 100%;
`;

const FormJSCustomStyling = createGlobalStyle`
  ${() => css`
    .fjs-container {
      .fjs-form-field button[type='submit'],
      .fjs-powered-by {
        display: none;
      }
    }

    @media (width >= ${breakpoints.lg.width}) {
      .fjs-form {
        margin-left: -8px;
        margin-right: -8px;
      }
    }
  `}
`;

const Layer = styled(BaseLayer)`
  width: 100%;
  max-width: 900px;
`;

const WarningFilled = styled(BaseWarning)`
  fill: var(--cds-support-error);
`;

const ValidationMessageContainer = styled.div`
  margin-block: var(--cds-spacing-06);
`;

const HorizontalRule = styled.hr`
  border: 0;
  height: 1px;
  background: var(--cds-border-subtle);
`;

export {
  FormRoot,
  FormJSCustomStyling,
  Layer,
  WarningFilled,
  ValidationMessageContainer,
  HorizontalRule,
};
