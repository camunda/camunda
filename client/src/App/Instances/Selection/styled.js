import styled from 'styled-components';
import {themed, themeStyle, Colors} from 'modules/theme';

import {RemoveItem} from 'modules/components/Icon';

import BadgeComponent from 'modules/components/Badge';

const themedWith = (dark, light) => {
  return themeStyle({
    dark,
    light
  });
};

export const Selection = themed(styled.div`
  width: 442px;
`);

export const Header = themed(styled.div`
  /* Positioning */

  /* Display & Box Model */
  display: flex;
  align-items: center;
  height: 32px;
  padding-left: 9px;
  padding-right: 21px;

  /* Color */
  color: ${({isOpen}) =>
    isOpen ? '#ffffff' : themedWith('#ffffff', Colors.uiLight06)};
  background: ${({isOpen}) =>
    isOpen ? Colors.selections : themedWith(Colors.uiDark03, Colors.uiLight02)};

  /* Text */
  font-size: 15px;
  font-weight: 600;

  /* Other */
  border-style: solid;
  border-width: 1px 0 1px 1px;
  border-color: ${({isOpen}) =>
    isOpen ? '#659fff' : themedWith(Colors.uiDark05, Colors.uiLight05)};
  border-radius: 3px 0 0 ${({isOpen}) => (isOpen ? 0 : '3px')};
`);

export const Headline = styled.div`
  padding-left: 7px;
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

export const Badge = styled(BadgeComponent)`
  top: 2px;
`;

export const OptionLabel = styled.label`
  margin-left: 8px;
`;

export const Instance = themed(styled.div`
  display: flex;
  align-items: center;
  height: 31px;
  padding-left: 10px;
  color: ${themedWith('#ffffff', Colors.uiDark04)};
  font-size: 13px;

  & * {
    top: 1px;
  }

  &:nth-child(even) {
    background: ${themedWith(
      Colors.darkInstanceEven,
      Colors.lightInstanceEven
    )};

    border-bottom: 1px solid ${themedWith(Colors.uiDark02, Colors.uiLight05)};
  }

  &:nth-child(odd) {
    background: ${themedWith(Colors.darkInstanceOdd, Colors.lightInstanceOdd)};
    border-bottom: 1px solid ${themedWith(Colors.uiDark02, Colors.uiLight05)};
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
  padding: 8px 21px 7px 0;
  color: #ffffff;
  background: ${Colors.selections};
  border-radius: 0 0 0 3px;
  border-width: 1px 0 1px 1px;
  border-style: solid;
  border-color: #659fff;
`;

export const MoreInstances = styled.div`
  font-size: 13px;
`;
