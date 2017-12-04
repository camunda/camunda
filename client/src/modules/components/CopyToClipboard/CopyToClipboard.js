import React from 'react';
import {Input, Button} from 'components'

import './CopyToClipboard.css';



export default class CopyToClipboard extends React.Component {
  
  copyText = (event) => {
    this.modalText.select();
    document.execCommand("Copy");
  }
  
  textArea = modalText => {
    this.modalText = modalText;
  }
  
  render() {
    return(
      <div className={'CopyToClipboard' + (this.props.className ? ' ' + this.props.className : '')}>
        <Input reference={this.textArea} className='CopyToClipboard__input' readOnly value={this.props.value}/>
        <Button className='CopyToClipboard__button' onClick={this.copyText}>Copy</Button>
      </div>
    );
  }
  
}