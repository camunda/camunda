/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {TaskDetailsRow as BaseTaskDetailsRow} from './TaskDetailsLayout';

const Container = styled.div`
  width: 100%;
  height: 100%;
  background-color: var(--cds-background);
  display: flex;
  justify-content: center;
  align-items: center;
`;

const TaskDetailsRow = styled(BaseTaskDetailsRow)`
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: end;
  text-align: right;
`;

type Props = {
  children: React.ReactNode;
};

const DetailsFooter: React.FC<Props> = ({children}) => {
  return (
    <Container>
      <TaskDetailsRow>{children}</TaskDetailsRow>
    </Container>
  );
};

export {DetailsFooter};
