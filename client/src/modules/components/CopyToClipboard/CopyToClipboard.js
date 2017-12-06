import React from 'react';
import {Input, Button} from 'components'

import './CopyToClipboard.css';



export default class CopyToClipboard extends React.Component {
  
  copyText = (event) => {
    this.inputElement.select();
    document.execCommand("Copy");
  }
  
  storeInputElement = inputElement => {
    this.inputElement = inputElement;
  }
  
  render() {
    return(
      <div className={'CopyToClipboard' + (this.props.className ? ' ' + this.props.className : '')}>
        <Input reference={this.storeInputElement} className='CopyToClipboard__input' readOnly value={this.props.value}/>
        <Button className='CopyToClipboard__button' onClick={this.copyText}>Copy</Button>
      </div>
    );
  }
  
}