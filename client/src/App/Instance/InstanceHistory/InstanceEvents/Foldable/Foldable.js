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
    folded: true
  };

  toggleFold = () => {
    const {folded} = this.state;
    this.setState({folded: !folded});
  };

  render() {
    const children = Children.map(this.props.children, child =>
      cloneElement(child, {
        folded: this.state.folded,
        toggleFold: this.toggleFold
      })
    );

    return <Styled.Foldable>{children}</Styled.Foldable>;
  }
}

Foldable.Summary = function Summary({toggleFold, folded, children, ...props}) {
  return (
    <Styled.Summary {...props}>
      <Styled.FoldButton onClick={toggleFold}>
        {!folded ? <Styled.DownIcon /> : <Styled.RightIcon />}
      </Styled.FoldButton>
      {children}
    </Styled.Summary>
  );
};

Foldable.Summary.propTypes = {
  toggleFold: PropTypes.func,
  folded: PropTypes.bool,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};

Foldable.Details = function Details(props) {
  return <Styled.Details {...props} />;
};
