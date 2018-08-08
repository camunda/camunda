import React from 'react';
import PropTypes from 'prop-types';

import {Down} from 'modules/components/Icon';
import {DROPDOWN_PLACEMENT} from 'modules/constants';

import Menu from './Menu';
import Option from './Option';

import * as Styled from './styled';

export default class Dropdown extends React.Component {
  static propTypes = {
    /** The content that is visible on the dropdown trigger. Must be non-interactive phrasing content. Supported labels types: string, component.*/
    label: PropTypes.node.isRequired,
    /** This defines if the dropdown opens to the top or bottom.*/
    placement: PropTypes.oneOf(['top', 'bottom']),
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

  // render Dropdown button with passed text or icon as label;
  getLabelType = label =>
    typeof label === 'object' ? (
      label
    ) : (
      <Styled.LabelWrapper>{label}</Styled.LabelWrapper>
    );

  childrenWithProps = () => {
    return React.Children.map(this.props.children, child => {
      return React.cloneElement(child, {
        placement: this.props.placement || DROPDOWN_PLACEMENT.BOTTOM
      });
    });
  };

  render() {
    return (
      <Styled.Dropdown innerRef={this.storeContainer}>
        <Styled.Button onClick={this.toggleOpen}>
          {this.getLabelType(this.props.label)}
          <Down />
        </Styled.Button>
        {this.state.open && (
          <Menu placement={this.props.placement || DROPDOWN_PLACEMENT.BOTTOM}>
            {this.childrenWithProps()}
          </Menu>
        )}
      </Styled.Dropdown>
    );
  }
}
// export Dropdown-option component
Dropdown.Option = Option;
