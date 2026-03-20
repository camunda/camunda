/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Container,
  Table,
  Th,
  Td,
  NameContainer,
  InstanceName,
  IncidentCount,
  AdditionalContent,
} from './styled';
import {StateIcon} from 'modules/components/StateIcon';
import pluralSuffix from 'modules/utils/pluralSuffix';

type Column = {
  title?: string;
  content: React.ReactNode;
  dataTestId?: string;
  hideOverflowingContent?: boolean;
  hidden?: boolean;
};

type Props = {
  state: React.ComponentProps<typeof StateIcon>['state'];
  instanceName: string;
  incidentsCount?: number;
  headerColumns: string[];
  bodyColumns: Column[];
  additionalContent?: React.ReactNode;
  hideBottomBorder?: boolean;
};

const InstanceHeader: React.FC<Props> = ({
  state,
  headerColumns,
  bodyColumns,
  instanceName,
  incidentsCount = 0,
  additionalContent,
  hideBottomBorder = false,
}) => {
  return (
    <Container
      data-testid="instance-header"
      $hideBottomBorder={hideBottomBorder}
    >
      <StateIcon state={state} size={24} data-testid={`${state}-icon`} />

      <NameContainer title={instanceName}>
        <InstanceName>{instanceName}</InstanceName>
        {incidentsCount > 0 && (
          <IncidentCount>
            {pluralSuffix(incidentsCount, 'incident')}
          </IncidentCount>
        )}
      </NameContainer>
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
            {bodyColumns.map((column, index) => {
              if (column.hidden) {
                return null;
              }
              return (
                <Td
                  key={index}
                  title={column.title}
                  data-testid={column.dataTestId}
                  $hideOverflowingContent={column.hideOverflowingContent}
                >
                  {column.content}
                </Td>
              );
            })}
          </tr>
        </tbody>
      </Table>
      <AdditionalContent>{additionalContent}</AdditionalContent>
    </Container>
  );
};

export {InstanceHeader};
