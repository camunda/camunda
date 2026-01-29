/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Container, Table, Th, Td, AdditionalContentWrapper} from './styled';
import {StateIcon} from 'modules/components/StateIcon';
import styled from 'styled-components';

const BackButtonWrapper = styled.div`
  margin-right: var(--cds-spacing-03);
`;

type Column = {
  title?: string;
  content: React.ReactNode;
  dataTestId?: string;
  hideOverflowingContent?: boolean;
};

type Props = {
  state: React.ComponentProps<typeof StateIcon>['state'];
  headerColumns: string[];
  bodyColumns: Column[];
  additionalContent?: React.ReactNode;
  hideBottomBorder?: boolean;
  backButton?: React.ReactNode;
  customContent?: React.ReactNode;
};

const InstanceHeader: React.FC<Props> = ({
  state,
  headerColumns,
  bodyColumns,
  additionalContent,
  hideBottomBorder = false,
  backButton,
  customContent,
}) => {
  return (
    <Container
      data-testid="instance-header"
      $hideBottomBorder={hideBottomBorder}
    >
      {backButton && <BackButtonWrapper>{backButton}</BackButtonWrapper>}
      <StateIcon state={state} size={24} data-testid={`${state}-icon`} />
      {customContent}
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
      {additionalContent && (
        <AdditionalContentWrapper>{additionalContent}</AdditionalContentWrapper>
      )}
    </Container>
  );
};

export {InstanceHeader};
