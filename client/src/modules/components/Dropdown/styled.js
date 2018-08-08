import styled, {css} from 'styled-components';
import {Colors, themeStyle} from 'modules/theme';

import {DROPDOWN_PLACEMENT} from 'modules/constants';

const PointerTop = css`
  top: 100%;
  left: 15px;
`;
const PointerBottom = css`
  bottom: 100%;
  right: 15px;
`;

export const PointerBasics = css`
  position: absolute;
  border: solid transparent;
  content: ' ';
  pointer-events: none;
  ${({placement}) =>
    placement === DROPDOWN_PLACEMENT.TOP ? PointerTop : PointerBottom};
`;

export const PointerBody = css`
  border-width: 7px;
  margin-right: -7px;
  border-bottom-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight02
  })};
  ${({placement}) =>
    placement === DROPDOWN_PLACEMENT.TOP ? 'transform: rotate(180deg)' : ''};
`;

export const PointerShadow = css`
  border-width: 8px;
  margin-right: -8px;
  border-bottom-color: ${themeStyle({
    dark: Colors.uiDark06,
    light: Colors.uiLight05
  })};

  ${({placement}) =>
    placement === DROPDOWN_PLACEMENT.TOP ? 'transform: rotate(180deg)' : ''};
`;

export const Button = styled.button`
  display: flex;
  align-items: center;

  border: none;
  outline: none;

  color: currentColor;
  background: none;

  font-family: IBMPlexSans;
  font-size: 15px;
  font-weight: 600;

  cursor: pointer;

  svg {
    vertical-align: text-bottom;
  }
`;

export const LabelWrapper = styled.div`
  margin-right: 8px;
`;

export const Dropdown = styled.div`
  display: inline-block;
  position: relative;
`;
