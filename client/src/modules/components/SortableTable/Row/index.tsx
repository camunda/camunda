/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {TR, TD} from './styled';
import {Checkbox} from '../Checkbox';

type Column = {
  cellContent: React.ReactNode;
  dataTestId?: string;
};

type SelectionType = 'checkbox' | 'row' | 'none';

type Props = {
  id: string;
  content: Column[];
  ariaLabel: string;
  selectionType?: SelectionType;
  isSelected?: boolean;
  onSelect?: () => void;
};

const Row: React.FC<Props> = React.memo(
  ({
    selectionType = 'none',
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
        isClickable={selectionType === 'row'}
        onClick={() => {
          if (selectionType === 'row') {
            onSelect?.();
          }
        }}
      >
        {content.map(({cellContent, dataTestId}, index) => {
          return (
            <TD key={index} data-testid={dataTestId}>
              <>
                {index === 0 && selectionType === 'checkbox' && (
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
export type {SelectionType};
