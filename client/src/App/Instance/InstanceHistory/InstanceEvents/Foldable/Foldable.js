import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default class Foldable extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    indentation: PropTypes.number
  };

  state = {
    isFolded: true
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
          isFolded: this.state.isFolded,
          toggleFold: this.toggleFold,
          indentation: this.props.indentation
        })
    );

    return <React.Fragment>{children}</React.Fragment>;
  }
}

Foldable.Summary = function Summary({
  toggleFold,
  isFoldable,
  indentation = 0,
  onSelection,
  children,
  ...props
}) {
  return (
    <Styled.Summary {...props}>
      {!isFoldable ? null : (
        <Styled.FoldButton onClick={toggleFold} indentation={indentation}>
          {!props.isFolded ? (
            <Styled.DownIcon isSelected={props.isSelected} />
          ) : (
            <Styled.RightIcon isSelected={props.isSelected} />
          )}
        </Styled.FoldButton>
      )}
      <Styled.SummaryLabel
        isFoldable={isFoldable}
        indentation={indentation}
        isSelected={props.isSelected}
        onClick={onSelection}
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
  indentation: PropTypes.number,
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

Foldable.Details = function Details(props) {
  return <Styled.Details {...props} />;
};
