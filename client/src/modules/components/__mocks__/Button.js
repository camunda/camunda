import React from 'react';

export default class Button extends React.Component {
  render() {
    const {props} = this;
    return (
      <button {...props} active={props.active ? 'true' : undefined}>
        {props.children}
      </button>
    );
  }
}
