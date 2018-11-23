import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

export default class Collapse extends React.Component {
  static propTypes = {
    content: PropTypes.node,
    header: PropTypes.node,
    buttonTitle: PropTypes.string
  };

  state = {
    isCollapsed: true
  };

  handleToggle = () => {
    this.setState(prevState => {
      return {
        isCollapsed: !prevState.isCollapsed
      };
    });
  };

  render() {
    return (
      <Styled.Collapse>
        <Styled.Button
          onClick={this.handleToggle}
          title={this.props.buttonTitle}
        >
          <Styled.Icon rotated={this.state.isCollapsed} />
        </Styled.Button>
        {this.props.header}
        {!this.state.isCollapsed && this.props.content}
      </Styled.Collapse>
    );
  }
}
