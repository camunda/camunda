/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as Styled from './styled';

type Column = {
  variant: 'block' | 'custom';
  width?: string;
  customSkeleton?: React.ReactNode;
};

type Props = {
  columns: Array<Column>;
};

const NUMBER_OF_ROWS = 50;

const TableSkeleton: React.FC<Props> = ({columns}) => {
  return (
    <tbody data-testid="table-skeleton">
      {[...new Array(NUMBER_OF_ROWS)].map((_, index) => (
        <tr key={index}>
          {columns.map(({variant, width, customSkeleton}, index) => {
            return (
              <Styled.Td key={index}>
                {variant === 'custom' && customSkeleton}
                {variant === 'block' && (
                  <Styled.Block width={width ?? '100%'} />
                )}
              </Styled.Td>
            );
          })}
        </tr>
      ))}
    </tbody>
  );
};

export {TableSkeleton};
