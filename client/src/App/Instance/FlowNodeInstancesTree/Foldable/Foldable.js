import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default class Foldable extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    isFoldable: PropTypes.bool
  };

  state = {
    isFolded: this.props.isFoldable
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
  ...props
}) {
  return (
    <Styled.Summary {...props}>
      {isFoldable ? (
        <Styled.FoldButton onClick={toggleFold} isFolded={isFolded}>
          {!isFolded ? <Styled.DownIcon /> : <Styled.RightIcon />}
        </Styled.FoldButton>
      ) : null}
      <Styled.FocusButton isSelected={isSelected} onClick={onSelection} />
      <Styled.SummaryLabel>{children}</Styled.SummaryLabel>
    </Styled.Summary>
  );
};

Foldable.Summary.propTypes = {
  toggleFold: PropTypes.func,
  isFoldable: PropTypes.bool,
  isFolded: PropTypes.bool,
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
