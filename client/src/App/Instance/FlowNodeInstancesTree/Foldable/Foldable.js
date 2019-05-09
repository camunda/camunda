/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default class Foldable extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    isFoldable: PropTypes.bool,
    isFolded: PropTypes.bool
  };

  state = {
    isFolded: this.props.isFolded,
    isFoldable: this.props.isFoldable
  };

  toggleFold = () => {
    const {isFolded} = this.state;
    this.setState({isFolded: !isFolded});
  };

  render() {
    const children = Children.map(
      this.props.children,
      child =>
        child &&
        cloneElement(child, {
          isFoldable: this.props.isFoldable,
          isFolded: this.state.isFolded,
          toggleFold: this.toggleFold
        })
    );

    return <React.Fragment>{children}</React.Fragment>;
  }
}

Foldable.Summary = function Summary({
  toggleFold,
  isFoldable,
  isFolded,
  indentation = 0,
  isSelected,
  onSelection,
  children,
  isLastChild,
  ...props
}) {
  return (
    <Styled.Summary {...props}>
      {isFoldable ? (
        <Styled.ExpandButton
          onClick={toggleFold}
          isExpanded={!isFolded}
          expandTheme="foldable"
        />
      ) : null}
      <Styled.FocusButton showHoverState={!isSelected} onClick={onSelection} />
      <Styled.SummaryLabel
        isSelected={isSelected}
        showPartialBorder={!isFolded}
        showFullBorder={isLastChild}
      >
        {children}
      </Styled.SummaryLabel>
    </Styled.Summary>
  );
};

Foldable.Summary.propTypes = {
  toggleFold: PropTypes.func,
  isFoldable: PropTypes.bool,
  isFolded: PropTypes.bool,
  isLastChild: PropTypes.bool,
  isSelected: PropTypes.bool,
  onSelection: PropTypes.func,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};

Foldable.Summary.defaultProps = {
  isFoldable: true
};

Foldable.Details = function Details({isFolded, ...props}) {
  return <Styled.Details {...props} showChildScope={!isFolded} />;
};
