import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default class Foldable extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  state = {
    isFolded: true
  };

  toggleFold = () => {
    const {isFolded} = this.state;
    this.setState({isFolded: !isFolded});
  };

  render() {
    const children = Children.map(this.props.children, child =>
      cloneElement(child, {
        isFolded: this.state.isFolded,
        toggleFold: this.toggleFold
      })
    );

    return <Styled.Foldable>{children}</Styled.Foldable>;
  }
}

Foldable.Summary = function Summary({
  toggleFold,
  isFoldable,
  isFolded,
  children,
  ...props
}) {
  return (
    <Styled.Summary {...props}>
      {!isFoldable ? null : (
        <Styled.FoldButton onClick={toggleFold}>
          {!isFolded ? <Styled.DownIcon /> : <Styled.RightIcon />}
        </Styled.FoldButton>
      )}
      <Styled.SummaryLabel isFoldable={isFoldable}>
        {children}
      </Styled.SummaryLabel>
    </Styled.Summary>
  );
};

Foldable.Summary.propTypes = {
  toggleFold: PropTypes.func,
  isFoldable: PropTypes.bool,
  isFolded: PropTypes.bool,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  isBold: PropTypes.bool
};

Foldable.Summary.defaultProps = {
  isFoldable: true
};

Foldable.Details = function Details(props) {
  return <Styled.Details {...props} />;
};
