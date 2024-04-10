/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
