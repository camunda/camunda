/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {TR, TD} from './styled';
import {Checkbox} from '../Checkbox';

type Column = {
  cellContent: React.ReactNode;
  dataTestId?: string;
};

type Props = {
  id: string;
  content: Column[];
  ariaLabel: string;
  isSelectable?: boolean;
  isSelected?: boolean;
  onSelect?: () => void;
};

const Row: React.FC<Props> = React.memo(
  ({
    isSelectable = false,
    isSelected = false,
    id,
    content,
    onSelect,
    ariaLabel,
  }) => {
    return (
      <TR
        selected={isSelected}
        aria-label={ariaLabel}
        aria-selected={isSelected}
      >
        {content.map(({cellContent, dataTestId}, index) => {
          return (
            <TD key={index} data-testid={dataTestId}>
              <>
                {index === 0 && isSelectable && (
                  <Checkbox
                    data-testid="instance-checkbox"
                    title={`Select instance ${id}`}
                    checked={isSelected}
                    onCmInput={onSelect}
                  />
                )}

                {cellContent}
              </>
            </TD>
          );
        })}
      </TR>
    );
  }
);
export {Row};
