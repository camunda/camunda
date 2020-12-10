/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Children, cloneElement, useState, isValidElement} from 'react';

import {
  SummaryContainer,
  ExpandButton,
  FocusButton,
  SummaryLabel,
} from './styled';

type Props = {
  children?: React.ReactNode;
  isFoldable?: boolean;
  isFolded?: boolean;
};

const Foldable = ({children, isFoldable, ...props}: Props) => {
  const [isFolded, setIsFolded] = useState(props.isFolded);

  return (
    <>
      {Children.map(children, (child) => {
        if (isValidElement(child)) {
          return cloneElement(child, {
            isFoldable: isFoldable,
            isFolded: isFolded,
            toggleFold: () => {
              setIsFolded((isFolded) => !isFolded);
            },
          });
        } else {
          return null;
        }
      })}
    </>
  );
};

type SummaryProps = {
  toggleFold?: () => void;
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

const Summary: React.FC<SummaryProps> = ({
  toggleFold,
  isFoldable = true,
  isFolded,
  indentation = 0,
  isSelected,
  onSelection,
  children,
  isLastChild,
  nodeName,
  ...props
}) => {
  return (
    <SummaryContainer {...props}>
      {isFoldable ? (
        <ExpandButton
          // @ts-expect-error ts-migrate(2769) FIXME: Property 'onClick' does not exist on type 'Intrins... Remove this comment to see the full error message
          onClick={toggleFold}
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
};

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
