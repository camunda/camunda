import React from 'react';
import ReactDOM from 'react-dom';


import './Modal.css';

export default class Modal extends React.Component {
  constructor(props) {
    super(props);
    this.el = document.createElement('div');
  }

  componentDidMount() {
    document.body.appendChild(this.el);
    this.fixPositioning();
  }

  componentWillUnmount() {
    document.body.removeChild(this.el);
  }

  storeContainer = node => {
    this.container = node;
  }

  fixPositioning = () => {
    if(this.container) {
      this.container.style.marginTop = -this.container.clientHeight / 2 + 'px';
      this.container.style.marginLeft = -this.container.clientWidth / 2 + 'px';
    }
  }

  onBackdropClick = evt => {
    evt.stopPropagation();

    const handler = this.props.onClose;
    handler && handler();
  }

  catchClick = evt => {
    if(!evt.nativeEvent.isCloseEvent) {
      evt.stopPropagation();
    }
  }

  render() {
    const {open, children} = this.props;

    if(open) {
      return ReactDOM.createPortal(
        <div className='Modal' onClick={this.onBackdropClick}>
          <div className={`Modal__container ${this.props.className || ''}`} ref={this.storeContainer} onClick={this.catchClick}>
            {children}
          </div>
        </div>,
      this.el);
    }

    return null;
  }

  componentDidUpdate() {
    this.fixPositioning();
  }
}

Modal.Header = function({children}) {
  return (<div className='Modal__header'>
    <h1 className='Modal__heading'>{children}</h1>
    <button className='Modal__close-button' onClick={evt => evt.nativeEvent.isCloseEvent = true}></button>
  </div>);
}

Modal.Content = function({children}) {
  return (<div className='Modal__content'>
    {children}
  </div>);
}

Modal.Actions = function({children}) {
  return (<div className='Modal__actions'>
    {children}
  </div>);
}
