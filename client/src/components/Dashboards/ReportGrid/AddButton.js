import React from 'react';

import './AddButton.css'

export default function AddButton (props) {

  const {buttonSize, gridMargin, gridSize, buttonTop, buttonLeft} = props;

  const buttonStyle = {
    top: (gridSize * buttonTop) + gridMargin + 'px',
    left: (gridSize * buttonLeft) + gridMargin + 'px',
    width: gridSize * buttonSize - gridMargin + 'px',
    height: gridSize * buttonSize - gridMargin + 'px'
  };

  return (
    <div className='add-button--container' onClick={props.onClick} style={buttonStyle}>

      <div className='add-button--add-sign-container'>
        <div className='add-button--add-sign'>&#65291;</div>
      </div>
      <div className='add-button--add-text'>Add a report</div>
    </div>)
}
