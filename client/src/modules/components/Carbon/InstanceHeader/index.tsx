/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, Table, Th, Td} from './styled';
import {StateIcon} from 'modules/components/Carbon/StateIcon';

type Column = {
  title?: string;
  content: React.ReactNode;
  dataTestId?: string;
};

type Props = {
  state: InstanceEntityState | DecisionInstanceEntityState;
  headerColumns: string[];
  bodyColumns: Column[];
  additionalContent?: React.ReactNode;
  hideBottomBorder?: boolean;
};

const InstanceHeader: React.FC<Props> = ({
  state,
  headerColumns,
  bodyColumns,
  additionalContent,
  hideBottomBorder = false,
}) => {
  return (
    <Container
      data-testid="instance-header"
      $hideBottomBorder={hideBottomBorder}
    >
      <StateIcon state={state} size={24} />

      <Table>
        <thead>
          <tr>
            {headerColumns.map((column, index) => (
              <Th key={index}>{column}</Th>
            ))}
          </tr>
        </thead>
        <tbody>
          <tr>
            {bodyColumns.map(({title, content, dataTestId}, index) => {
              return (
                <Td key={index} title={title} data-testid={dataTestId}>
                  {content}
                </Td>
              );
            })}
          </tr>
        </tbody>
      </Table>
      {additionalContent}
    </Container>
  );
};

export {InstanceHeader};
