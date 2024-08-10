/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Container, Table, Th, Td} from './styled';
import {StateIcon} from 'modules/components/StateIcon';

type Column = {
  title?: string;
  content: React.ReactNode;
  dataTestId?: string;
  hideOverflowingContent?: boolean;
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
      <StateIcon state={state} size={24} data-testid={`${state}-icon`} />

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
            {bodyColumns.map(
              ({title, content, dataTestId, hideOverflowingContent}, index) => {
                return (
                  <Td
                    key={index}
                    title={title}
                    data-testid={dataTestId}
                    $hideOverflowingContent={hideOverflowingContent}
                  >
                    {content}
                  </Td>
                );
              },
            )}
          </tr>
        </tbody>
      </Table>
      {additionalContent}
    </Container>
  );
};

export {InstanceHeader};
