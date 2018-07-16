import styled from 'styled-components';
import {themed, themeStyle, Colors} from 'modules/theme';

import {RemoveItem} from 'modules/components/Icon';

export const Selection = themed(styled.div`
  width: 443px;
`);

const themedText = themeStyle({
  dark: '#ffffff',
  light: Colors.uiLight06
});

const themedBorder = themeStyle({
  dark: Colors.uiDark05,
  light: Colors.uiLight05
});

const themedBackground = themeStyle({
  dark: Colors.uiDark03,
  light: Colors.uiLight02
});

export const Header = themed(styled.div`
  /* Positioning */

  /* Display & Box Model */
  display: flex;
  align-items: center;
  height: 32px;
  padding-left: 9px;
  padding-right: 22px;

  /* Color */
  color: ${({isOpen}) => (isOpen ? '#ffffff' : themedText)};
  background: ${({isOpen}) => (isOpen ? Colors.selections : themedBackground)};

  /* Text */
  font-size: 15px;
  font-weight: 600;

  /* Other */
  border-style: solid;
  border-width: 1px 0 1px 1px;
  border-color: ${({isOpen}) => (isOpen ? '#659fff' : themedBorder)};
  border-radius: 3px 0 0 ${({isOpen}) => (isOpen ? 0 : '3px')};
`);

export const Headline = styled.div`
  padding: 0 7px 0 9px;
`;

export const Actions = styled.div`
  display: flex;
  align-items: center;
  margin-left: auto;
`;

export const DropdownTrigger = styled.div`
  display: flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  margin-right: 10px;
  border-right: 1px solid ${'rgba(255, 255, 255, 0.15)'};
`;

export const DeleteIcon = styled(RemoveItem)`
  opacity: 0.45;
`;

export const Instance = themed(styled.div`
  display: flex;
  align-items: center;
  height: 31px;
  padding-left: 10px;
  color: ${themeStyle({dark: '#ffffff', light: Colors.uiDark04})};
  font-size: 13px;

  & * {
    top: 1px;
  }

  &:nth-child(even) {
    background: ${themeStyle({
      dark: Colors.darkInstanceEven,
      light: Colors.lightInstanceEven
    })};
    border-bottom: 1px solid
      ${themeStyle({
        dark: Colors.uiDark02,
        light: Colors.uiLight05
      })};
  }

  &:nth-child(odd) {
    background: ${themeStyle({
      dark: Colors.darkInstanceOdd,
      light: Colors.lightInstanceOdd
    })};
    border-bottom: 1px solid
      ${themeStyle({
        dark: Colors.uiDark02,
        light: Colors.uiLight05
      })};
  }
`);

export const WorkflowName = styled.div`
  width: 97px;
  margin: 0 64px 0 4px;
`;

// TODO: change margin when badge is added.
export const InstanceId = styled.div`
  margin: 0 171px 0 0;
`;

export const Footer = styled.div`
  display: flex;
  justify-content: flex-end;
  align-items: center;
  height: 32px;
  padding: 8px 22px 7px 0;
  color: #ffffff;
  background: ${Colors.selections};
  border-radius: 0 0 0 3px;
  border: solid 1px #659fff;
`;

export const MoreInstances = styled.div`
  font-size: 13px;
`;
