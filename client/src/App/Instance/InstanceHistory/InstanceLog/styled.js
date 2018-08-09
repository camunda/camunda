import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';
import {Document} from 'modules/components/Icon';
import BasicFlowNodeIcon from 'modules/components/FlowNodeIcon';
import {ACTIVITY_STATE} from 'modules/constants';
import withStrippedProps from 'modules/utils/withStrippedProps';

export const InstanceLog = themed(styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;

  overflow: auto;
  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  border-top: none;
  border-bottom: none;
`);

const backgroundColorStyle = css`
  &:nth-child(odd) {
    background-color: ${({theme, isSelected}) => {
      if (isSelected) {
        return Colors.selections;
      } else if (theme === 'dark') {
        return Colors.uiDark02;
      } else {
        return Colors.lightItemOdd;
      }
    }};
  }

  &:nth-child(even) {
    background-color: ${({theme, isSelected}) => {
      if (isSelected) {
        return Colors.selections;
      } else if (theme === 'dark') {
        return Colors.darkItemEven;
      } else {
        return Colors.lightItemEven;
      }
    }};
  }
`;

const colorStyle = css`
  color: ${({theme, isSelected, state}) => {
    if (state === ACTIVITY_STATE.INCIDENT) {
      return;
    } else {
      return isSelected || theme === 'dark'
        ? 'rgba(255, 255, 255, 0.9)'
        : Colors.uiDark04;
    }
  }};
`;

const iconPositionStyle = css`
  position: relative;
  top: 3px;
`;
export const LogEntry = themed(styled.button`
  background: transparent;
  margin: 0;
  border: none;
  padding: 5px;
  padding-left: 43px;
  height: 32px;
  text-align: left;

  ${backgroundColorStyle};
  ${colorStyle};

  cursor: pointer;

  font-size: 14px;
  font-weight: ${({isSelected}) => (!!isSelected ? 'bold' : 'normal')};
`);

export const Header = themed(styled(LogEntry)`
  padding-left: 23px;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(69, 70, 78, 0.9)'
  })};
  font-weight: bold;
`);

export const DocumentIcon = themed(styled(
  withStrippedProps(['isSelected'])(Document)
)`
  width: 16px;
  height: 16px;
  margin-right: 8px;

  ${colorStyle};
  ${iconPositionStyle};
`);

export const FlowNodeIcon = themed(styled(
  withStrippedProps(['isSelected'])(BasicFlowNodeIcon)
)`
  margin-right: 8px;

  ${colorStyle};
  ${iconPositionStyle};
`);
