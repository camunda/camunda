import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default class ExpansionPanel extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  state = {
    expanded: false
  };

  toggleExpand = () => {
    const {expanded} = this.state;
    this.setState({expanded: !expanded});
  };

  render() {
    const children = Children.map(this.props.children, child =>
      cloneElement(child, {
        expanded: this.state.expanded,
        toggleExpand: this.toggleExpand
      })
    );

    return <Styled.ExpansionPanel>{children}</Styled.ExpansionPanel>;
  }
}

ExpansionPanel.Summary = function Summary({
  toggleExpand,
  expanded,
  children,
  ...props
}) {
  return (
    <Styled.Summary {...props}>
      <Styled.ExpandButton onClick={toggleExpand}>
        {expanded ? <Styled.DownIcon /> : <Styled.RightIcon />}
      </Styled.ExpandButton>
      {children}
    </Styled.Summary>
  );
};

ExpansionPanel.Summary.propTypes = {
  toggleExpand: PropTypes.func,
  expanded: PropTypes.bool,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};

ExpansionPanel.Details = function Details(props) {
  return <Styled.Details {...props} />;
};
