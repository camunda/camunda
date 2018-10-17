import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import Dropdown from 'modules/components/Dropdown';

export const DropdownOption = styled(Dropdown.Option)`
  padding: 0px;
`;

export const Footer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
`;

export const Wrapper = themed(styled.div`
  padding: 0 10px;
`);

export const SelectionWrapper = styled.div`
  position: absolute;
  top: -4px;
  left: 0;
`;

export const PaginatorWrapper = styled.div`
  position: relative;
  bottom: 3px;
`;

export const SelectionButton = themed(styled.button`
  font-family: IBMPlexSans;
  font-size: 13px;
  font-weight: 600;

  border: none;
  outline: none;

  height: 26px;
  padding: 4px 11px 5px 11px;


  /* Color */
  background: ${Colors.selections};
  color: rgba(255, 255, 255, 1);

  border-radius: 13px;
  border: none;

  & > svg {
    vertical-align: text-bottom;
    margin-right: 8px;
  }

  &:focus {
    box-shadow: ${themeStyle({
      dark: `0 0 0 1px ${Colors.focusOuter},0 0 0 4px ${Colors.darkFocusInner}`,
      light: `0 0 0 1px ${Colors.focusOuter}, 0 0 0 4px ${
        Colors.lightFocusInner
      }`
    })}
`);
