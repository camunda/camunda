import React from 'react';

import * as Styled from './styled';

export default class Dropdown extends React.Component {
  state = {open: false};

  toggleOpen = () => {
    this.setState({open: !this.state.open});
  };

  close = ({target}) => {
    if (!this.container.contains(target)) {
      this.setState({open: false});
    }
  };

  componentDidMount() {
    document.body.addEventListener('click', this.close, true);
  }

  render() {
    return (
      <Styled.Dropdown innerRef={this.storeContainer}>
        <Styled.Label onClick={this.toggleOpen}>
          {this.props.label}
        </Styled.Label>
        {this.state.open && (
          <Styled.DropdownMenu>{this.props.children}</Styled.DropdownMenu>
        )}
      </Styled.Dropdown>
    );
  }

  storeContainer = node => {
    this.container = node;
  };

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close, true);
  }
}

Dropdown.Option = function DropdownOption(props) {
  return <Styled.Option {...props}>{props.children}</Styled.Option>;
};
