/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Container = styled.div`
  ${({theme}) =>
    css`
      padding-top: ${theme.spacing13};
    `}
`;

const ImageContainer = styled.div`
  ${({theme}) => css`
    padding: ${theme.spacing04} 0;
    display: flex;
    justify-content: end;
    align-items: flex-start;
  `}
`;

const NewUserTextContainer = styled.div`
  ${({theme}) => css`
    padding-left: ${theme.spacing06};
    color: var(--cds-text-primary);

    & h3 {
      padding-bottom: ${theme.spacing03};
    }

    & p,
    & a {
      ${theme.bodyLong01};
    }

    & p:first-of-type,
    & p:nth-of-type(2) {
      padding-bottom: ${theme.spacing06};
    }
  `}
`;

const OldUserTextContainer = styled.div`
  ${({theme}) => css`
    padding-left: ${theme.spacing06};
    color: var(--cds-text-primary);
    display: flex;
    align-items: center;
  `}
`;

const Image = styled.img`
  width: 80px;
  min-width: 80px;
`;

export {
  Container,
  ImageContainer,
  OldUserTextContainer,
  NewUserTextContainer,
  Image,
};
