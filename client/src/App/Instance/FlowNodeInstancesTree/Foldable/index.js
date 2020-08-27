/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Children, cloneElement, useState} from 'react';
import isNil from 'lodash/isNil';
import PropTypes from 'prop-types';

import {
  SummaryContainer,
  ExpandButton,
  FocusButton,
  SummaryLabel,
} from './styled';

const Foldable = ({children, isFoldable, ...props}) => {
  const [isFolded, setIsFolded] = useState(props.isFolded);

  return Children.map(children, (child) => {
    if (!isNil(child)) {
      return cloneElement(child, {
        isFoldable: isFoldable,
        isFolded: isFolded,
        toggleFold: () => {
          setIsFolded((isFolded) => !isFolded);
        },
      });
    }

    return null;
  });
};

Foldable.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
  isFoldable: PropTypes.bool.isRequired,
  isFolded: PropTypes.bool.isRequired,
};

Foldable.Summary = function Summary({
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
}) {
  return (
    <SummaryContainer {...props}>
      {isFoldable ? (
        <ExpandButton
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

Foldable.Summary.propTypes = {
  toggleFold: PropTypes.func,
  isFoldable: PropTypes.bool,
  isFolded: PropTypes.bool,
  indentation: PropTypes.number,
  isLastChild: PropTypes.bool,
  isSelected: PropTypes.bool,
  onSelection: PropTypes.func,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
  nodeName: PropTypes.string,
};

Foldable.Details = function Details({isFolded, children}) {
  return isFolded ? null : children;
};

Foldable.Details.propTypes = {
  isFolded: PropTypes.bool,
  children: PropTypes.node,
};

export {Foldable};
