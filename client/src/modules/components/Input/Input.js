import React from 'react';

import './Input.css';

export default class Input extends React.PureComponent {
  render() {
    let allowedProps = {...this.props};
    delete allowedProps.reference;

    return (<input type='text' {...allowedProps} className={'Input' + (this.props.className ? ' ' + this.props.className : '')} ref={this.props.reference}>
      {this.props.children}
    </input>);
  }
}
