import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

import {Down} from 'modules/components/Icon';

export default class Dropdown extends React.Component {
  static propTypes = {
    /** The content that is visible on the dropdown trigger. Must be non-interactive phrasing content. */
    label: PropTypes.node.isRequired,
    /** The options of this dropdown. Each child should be a `Dropdown.Option` instance */
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  state = {open: false};

  componentDidMount() {
    document.body.addEventListener('click', this.close, true);
  }

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close, true);
  }

  storeContainer = node => {
    this.container = node;
  };

  toggleOpen = () => {
    this.setState({open: !this.state.open});
  };

  close = ({target}) => {
    if (!this.container.contains(target)) {
      this.setState({open: false});
    }
  };

  render() {
    return (
      <Styled.Dropdown innerRef={this.storeContainer}>
        <Styled.Label data-test-id="dropdown-label" onClick={this.toggleOpen}>
          {this.props.label}
          <Down />
        </Styled.Label>
        {this.state.open && (
          <Styled.DropdownMenu>{this.props.children}</Styled.DropdownMenu>
        )}
      </Styled.Dropdown>
    );
  }
}

Dropdown.Option = function DropdownOption(props) {
  return <Styled.Option {...props}>{props.children}</Styled.Option>;
};

Dropdown.Option.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};
