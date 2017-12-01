import React from 'react';

import './Input.css';

export default class Input extends React.PureComponent {
  render() {
    return (<input type='text' {...this.props} className={'Input ' + this.props.className} >
      {this.props.children}
    </input>);
  }
}
