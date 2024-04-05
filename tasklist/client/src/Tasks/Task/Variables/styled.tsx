/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {TaskDetailsRow} from 'modules/components/TaskDetailsLayout';
import styled, {css} from 'styled-components';
import {IconButton} from './IconButton';
import {
  StructuredListWrapper as BaseStructuredListWrapper,
  StructuredListCell as BaseStructuredListCell,
} from '@carbon/react';
import {rem} from '@carbon/elements';

const Form = styled.form`
  width: 100%;
  height: 100%;
`;

const Container = styled(TaskDetailsRow)`
  padding: 0;
  padding-bottom: var(--cds-spacing-05);
`;

const EmptyFieldsInformationIcon = styled(IconButton)`
  margin-right: var(--cds-spacing-01);
`;

const StructuredListWrapper = styled(BaseStructuredListWrapper)`
  height: min-content;
`;

const StructuredListCell = styled(BaseStructuredListCell)`
  vertical-align: middle;
`;

const OuterScrollableCellContent = styled.div`
  height: min-content;
  overflow-y: auto;
`;

const InnerScrollableCellContent = styled.div`
  max-height: 100px;
  height: min(min-content, 100%);
  overflow-y: auto;
`;

const ScrollableCellContent: React.FC<{
  children: React.ReactNode;
}> = ({children}) => {
  return (
    <OuterScrollableCellContent>
      <InnerScrollableCellContent>{children}</InnerScrollableCellContent>
    </OuterScrollableCellContent>
  );
};

type IconButtonsContainerProps = {
  $showExtraPadding?: boolean;
};

const IconButtonsContainer = styled.div<IconButtonsContainerProps>`
  ${({$showExtraPadding = false}) => css`
    display: flex;
    flex-direction: row;
    justify-content: flex-end;
    padding-right: ${$showExtraPadding
      ? css`calc(var(--cds-spacing-03) + 32px)`
      : css`var(--cds-spacing-03)`};
  `}
`;

const VariableNameCell = styled(StructuredListCell)`
  width: 200px;
`;

const VariableValueCell = styled(StructuredListCell)`
  overflow-y: hidden;
  word-break: break-all;
  height: min(100px, min-content);
`;

const ControlsCell = styled(StructuredListCell)`
  width: 80px;
`;

const PanelHeader = styled(TaskDetailsRow)`
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: ${rem(32)};
`;

export {
  Form,
  EmptyFieldsInformationIcon,
  Container,
  StructuredListWrapper,
  StructuredListCell,
  ScrollableCellContent,
  IconButtonsContainer,
  VariableNameCell,
  VariableValueCell,
  ControlsCell,
  PanelHeader,
};
