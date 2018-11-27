import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';
import {ReactComponent as Document} from 'modules/components/Icon/document.svg';
import BasicFlowNodeIcon from 'modules/components/FlowNodeIcon';
import withStrippedProps from 'modules/utils/withStrippedProps';

export const InstanceLog = themed(
  styled(
    withStrippedProps([
      'onActivityInstanceSelected',
      'activitiesDetails',
      'selectedActivityInstanceId'
    ])('div')
  )`
    flex: 1;
    display: flex;

    overflow: auto;
    border: solid 1px
      ${themeStyle({
        dark: Colors.uiDark04,
        light: Colors.uiLight05
      })};
    border-top: none;
    border-bottom: none;
    position: relative;
  `
);

export const EntriesContainer = styled.ul`
  position: absolute;
  height: 100%;
  width: 100%;
  min-height: min-content;
  margin: 0;
  padding: 0;
`;

const colorStyle = css`
  color: ${({theme, isSelected}) => {
    return isSelected || theme === 'dark'
      ? 'rgba(255, 255, 255, 0.9)'
      : Colors.uiDark04;
  }};
`;

export const LogEntryToggle = themed(styled.button`
  display: flex;
  align-items: center;
  position: absolute;
  left: 0;
  top: 0;

  height: 32px;
  width: 100%;

  margin: 0;
  border: none;
  padding-left: 43px;
  text-align: left;
  ${colorStyle};
  font-size: 14px;
  font-weight: ${({isSelected}) => (!!isSelected ? 'bold' : 'normal')};
`);

const backgroundColorStyle = css`
  &:nth-child(odd) {
    ${LogEntryToggle.WrappedComponent} {
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
  }

  &:nth-child(even) {
    ${LogEntryToggle.WrappedComponent} {
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
  }
`;

export const LogEntry = themed(styled.li`
  position: relative;
  height: 32px;

  ${backgroundColorStyle};
`);

export const HeaderToggle = themed(styled(LogEntryToggle)`
  padding-left: 23px;
  color: ${({theme, isSelected}) => {
    return isSelected || theme === 'dark'
      ? 'rgba(255, 255, 255, 0.9)'
      : 'rgba(69, 70, 78, 0.9)';
  }};
  font-weight: bold;
`);

export const DocumentIcon = themed(styled(
  withStrippedProps(['isSelected'])(Document)
)`
  width: 16px;
  height: 16px;
  margin-right: 8px;

  ${colorStyle};
`);

export const FlowNodeIcon = themed(styled(BasicFlowNodeIcon)`
  margin-right: 8px;
`);
