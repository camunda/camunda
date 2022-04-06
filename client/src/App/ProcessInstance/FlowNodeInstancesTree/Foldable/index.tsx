/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {Children, cloneElement, isValidElement} from 'react';

import {
  SummaryContainer,
  ExpandButton,
  FocusButton,
  SummaryLabel,
} from './styled';

type Props = {
  children?: React.ReactNode;
  isFoldable: boolean;
  isFolded: boolean;
  onToggle?: () => void;
};

const Foldable = ({
  children,
  isFoldable,
  onToggle,
  isFolded,
  ...props
}: Props) => {
  return (
    <>
      {Children.map(children, (child) => {
        if (isValidElement(child)) {
          return cloneElement(child, {isFoldable, isFolded, onToggle});
        } else {
          return null;
        }
      })}
    </>
  );
};

type SummaryProps = {
  onToggle?: () => void;
  isFoldable?: boolean;
  isFolded?: boolean;
  indentation?: number;
  isLastChild: boolean;
  isSelected: boolean;
  onSelection: () => void;
  children: React.ReactNode;
  nodeName?: string;
  'data-testid'?: string;
};

const Summary = React.forwardRef<HTMLDivElement, SummaryProps>(
  (
    {
      onToggle,
      isFoldable = true,
      isFolded,
      indentation = 0,
      isSelected,
      onSelection,
      children,
      isLastChild,
      nodeName,
      ...props
    },
    ref
  ) => {
    return (
      <SummaryContainer
        {...props}
        ref={ref}
        role="row"
        aria-selected={isSelected}
      >
        {isFoldable && onToggle !== undefined ? (
          <ExpandButton
            onClick={onToggle}
            isExpanded={!isFolded}
            iconButtonTheme="foldable"
            aria-label={isFolded ? `Unfold ${nodeName}` : `Fold ${nodeName}`}
          />
        ) : null}
        <FocusButton showHoverState={!isSelected} onClick={onSelection}>
          <SummaryLabel
            isSelected={isSelected}
            showPartialBorder={!isFolded}
            showFullBorder={isLastChild}
          >
            {children}
          </SummaryLabel>
        </FocusButton>
      </SummaryContainer>
    );
  }
);

type DetailsProps = {
  isFolded?: boolean;
  children?: React.ReactNode;
};
const Details: React.FC<DetailsProps> = ({isFolded, children}) => {
  return <>{isFolded ? null : children}</>;
};

Foldable.Summary = Summary;
Foldable.Details = Details;

export {Foldable};
