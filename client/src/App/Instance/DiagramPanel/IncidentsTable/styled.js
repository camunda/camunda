import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import Table from 'modules/components/Table';
const {TH} = Table;

export const FirstCell = styled.div`
  position: relative;
  padding-left: 23px;

  &:after {
    content: '-';
    position: absolute;
    top: 0;
    left: -51px;
    width: 35px;

    text-align: right;

    font-size: 11px;
    color: rgba(255, 255, 255, 0.6);
  }
`;

export const ErrorMessageCell = styled.div`
  margin-right: 10px;
  width: 404px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

export const Flex = styled.div`
  display: flex;
  align-items: center;
`;

export const FirstTH = themed(styled(TH)`
  position: relative;
  height: 100%;
  padding-left: 27px

  &:before {
    content: '';
    position: absolute;
    top: -1px;
    left: -52px;
    width: 51px;
    height: 100%;

    border-bottom: 1px solid
      ${themeStyle({
        dark: Colors.uiDark04,
        light: Colors.uiLight05
      })};
    border-top: 1px solid
      ${themeStyle({
        dark: Colors.uiDark04,
        light: Colors.uiLight05
      })};

    background-color: ${themeStyle({
      dark: Colors.uiDark03,
      light: Colors.uiLight04
    })};
  }

  &:after {
    content: '';
    display: block;
    position: absolute;
    top: -3px !important;
    left: 21px;
    width: 1px;
    height: 100% !important;
    margin-top: 0;

    background: ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  }
`);
