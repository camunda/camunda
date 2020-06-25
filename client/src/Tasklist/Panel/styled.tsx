/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import styled, {css} from 'styled-components';

interface BaseProps {
  hasRoundTopLeftCorner?: boolean;
  hasRoundTopRightCorner?: boolean;
  hasFooter?: boolean;
}

const Header = styled.div`
  height: 31px;
  line-height: 32px;
  font-size: 15px;
  font-weight: bold;
  background-color: ${({theme}) => theme.colors.ui02};
  color: ${({theme}) => theme.colors.ui06};
  padding: 3px 0 3px 19px;
  border-bottom: 1px solid ${({theme}) => theme.colors.ui05};
  display: flex;
  justify-content: space-between;
`;

const Base = styled.div<BaseProps>`
  display: grid;
  grid-template-columns: 100%;
  grid-template-rows: ${({hasFooter}) =>
    hasFooter ? '38px 1fr 38px' : '38px 1fr'};

  &,
  & ${Header} {
    ${({hasRoundTopLeftCorner}) =>
      hasRoundTopLeftCorner ? 'border-top-left-radius: 3px;' : ''}
    ${({hasRoundTopRightCorner}) =>
      hasRoundTopRightCorner ? 'border-top-right-radius: 3px;' : ''};
  }
`;

interface BodyProps {
  hasTransparentBackground?: boolean;
  extraBodyStyles?: ReturnType<typeof css>;
}

const Body = styled.div<BodyProps>`
  background-color: ${({hasTransparentBackground, theme}) =>
    hasTransparentBackground ? 'transparent' : theme.colors.ui04};
  ${({extraBodyStyles}) => extraBodyStyles};
`;

const Footer = styled.div`
  height: 37px;
  background-color: ${({theme}) => theme.colors.ui02};
  border-top: 1px solid ${({theme}) => theme.colors.ui05};
  color: ${({theme}) => theme.colors.text.copyrightNotice};
  font-size: 12px;
  text-align: right;
  line-height: 38px;
  padding-right: 20px;
`;

export {Base, Header, Body, Footer};
