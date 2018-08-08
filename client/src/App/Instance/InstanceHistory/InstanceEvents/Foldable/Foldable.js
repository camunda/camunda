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
  isFolded,
  children,
  ...props
}) {
  return (
    <Styled.Summary {...props}>
      <Styled.FoldButton onClick={toggleFold}>
        {!isFolded ? <Styled.DownIcon /> : <Styled.RightIcon />}
      </Styled.FoldButton>
      {children}
    </Styled.Summary>
  );
};

Foldable.Summary.propTypes = {
  toggleFold: PropTypes.func,
  isFolded: PropTypes.bool,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};

Foldable.Details = function Details(props) {
  return <Styled.Details {...props} />;
};
