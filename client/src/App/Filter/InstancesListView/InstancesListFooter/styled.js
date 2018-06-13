import styled from 'styled-components';
import {Colors} from 'modules/theme';

export const Pagination = styled.div`
  text-align: center;
`;

export const Page = styled.button`
  color: currentColor;
  outline: none;
  font-family: IBMPlexSans;
  font-size: 13px;
  cursor: pointer;
  padding: ${({withIcon}) => (withIcon ? '0' : '0 5px')};
  line-height: 18px;
  height: 18px;
  margin: 1px;
  vertical-align: top;
  ${({active}) => {
    if (active) {
      return `background-color: rgba(77, 144, 255, 0.9);
      border: 1px solid #007dff;
      cursor: default;`;
    } else {
      return `background-color: ${Colors.uiDark04};
      border: 1px solid ${Colors.uiDark05};`;
    }
  }} ${({disabled}) =>
    disabled ? 'background-color: #34353a; cursor: default;' : ''};
`;

export const PageSeparator = styled.div`
  vertical-align: top;
  display: inline-block;
  opacity: 0.9;
  font-size: 13px;
  height: 18px;
  width: 18px;
  text-align: center;
  line-height: 18px;
`;
