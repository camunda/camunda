import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import Dropdown from 'modules/components/Dropdown';

export const DropdownButtonStyles = {
  fontSize: '13px',
  fontWeight: 600,
  background: Colors.selections,
  height: '26px',
  borderRadius: '13px',
  border: 'none',
  padding: '4px 11px 5px 11px',
  color: 'rgba(255, 255, 255, 1)'
};

export const Wrapper = themed(styled.div`
  padding: 0 10px;
`);

export const DropdownOption = styled(Dropdown.Option)`
  padding: 0px;
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
