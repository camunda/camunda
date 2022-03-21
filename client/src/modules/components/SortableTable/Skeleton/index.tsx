/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {TR, TD, Block, Circle, SkeletonCheckboxBlock} from './styled';

type Column = {
  variant: 'block' | 'circle' | 'custom';
  width?: string;
  height?: string;
  customSkeleton?: React.ReactNode;
};

type Props = {
  columns: Array<Column>;
};

const NUMBER_OF_ROWS = 50;

const Skeleton: React.FC<Props> = ({columns}) => {
  return (
    <tbody data-testid="table-skeleton">
      {[...new Array(NUMBER_OF_ROWS)].map((_, index) => (
        <TR key={index}>
          {columns.map(({variant, width, height, customSkeleton}, index) => {
            return (
              <TD key={index}>
                {variant === 'custom' && customSkeleton}
                {variant === 'block' && <Block width={width ?? '100%'} />}
                {variant === 'circle' && (
                  <Circle width={width ?? '100%'} height={height ?? '%100'} />
                )}
              </TD>
            );
          })}
        </TR>
      ))}
    </tbody>
  );
};

export {Skeleton, SkeletonCheckboxBlock};
