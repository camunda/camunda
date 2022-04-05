/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, createGlobalStyle} from 'styled-components';

type ContainerProps = {
  hasFooter?: boolean;
};

const Container = styled.div<ContainerProps>`
  ${({hasFooter}) => css`
    display: grid;
    grid-template-columns: 100%;
    grid-template-rows: ${hasFooter ? 'auto 1fr 62px' : 'auto 1fr'};
    overflow-y: hidden;
  `}
`;

const FormContainer = styled.div`
  overflow-y: auto;
`;

const FormCustomStyling = createGlobalStyle`
  ${({theme}) => css`
    .fjs-container {
      height: min-content;

      .fjs-form-field-label {
        color: ${theme.colors.label01};
        font-size: 14px;
        font-weight: 600;
      }

      .fjs-input[type='text'],
      .fjs-input[type='number'],
      .fjs-textarea,
      .fjs-select {
        border: 1px solid ${theme.colors.ui05};

        :focus {
          box-shadow: ${theme.shadows.fakeOutline};
          border: 1px solid ${theme.colors.ui05};
          padding: 8px;
        }
      }

      .fjs-has-errors .fjs-input[type='text'],
      .fjs-has-errors .fjs-input[type='number'],
      .fjs-has-errors .fjs-textarea,
      .fjs-has-errors .fjs-select {
        border: 1px solid ${({theme}) => theme.colors.red};

        :focus {
          border: 1px solid ${({theme}) => theme.colors.ui05};
          box-shadow: ${({theme}) => theme.shadows.invalidInput};
        }
      }

      .fjs-powered-by,
      .fjs-form-field button[type='submit'] {
        display: none;
      }
    }
  `}
`;

export {Container, FormCustomStyling, FormContainer};
