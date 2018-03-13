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
    this.setFocus();
    new MutationObserver(this.fixPositioning).observe(this.el, {childList: true, subtree: true});
    window.addEventListener('resize', this.fixPositioning);
  }

  componentWillUnmount() {
    document.body.removeChild(this.el);
    window.removeEventListener('resize', this.fixPositioning);
  }

  storeContainer = node => {
    this.container = node;
  };

  fixPositioning = () => {
    if (this.container) {
      const windowHeight = Math.max(document.documentElement.clientHeight, window.innerHeight || 0);
      const margin = 30; // already set top  (15 px) + bottom  (15 px) margin
      let height = (windowHeight - this.container.clientHeight) / 2 - margin;
      height = Math.max(height, 0);
      this.container.style.marginTop = height + 'px';
      this.container.style.marginLeft = -this.container.clientWidth / 2 + 'px';
    }
  };

  setFocus = () => {
    if (this.container && this.props.open) {
      this.container.focus();
    }
  };

  onBackdropClick = evt => {
    evt.stopPropagation();

    const handler = this.props.onClose;
    handler && handler();
  };

  catchClick = evt => {
    if (!evt.nativeEvent.isCloseEvent) {
      evt.stopPropagation();
    }
  };

  handleKeyPress = evt => {
    if (evt.key === 'Escape') {
      const handler = this.props.onClose;
      handler && handler();
    }
  };

  render() {
    const {open, children} = this.props;

    if (open) {
      return ReactDOM.createPortal(
        <div className="Modal" onClick={this.onBackdropClick}>
          <div className="Modal__scroll-container">
            <div
              className={
                `Modal__content-container ${this.props.className || ''}` +
                (this.props.size ? ' Modal__content-container--' + this.props.size : '')
              }
              tabIndex="-1"
              ref={this.storeContainer}
              onClick={this.catchClick}
              onKeyDown={this.handleKeyPress}
            >
              {children}
            </div>
          </div>
        </div>,
        this.el
      );
    }

    return null;
  }

  componentDidUpdate(prevProps) {
    if (!prevProps.open && this.props.open) {
      this.setFocus();
    }

    this.fixPositioning();
  }
}

Modal.Header = function({children}) {
  return (
    <div className="Modal__header">
      <h1 className="Modal__heading">{children}</h1>
      <button
        className="Modal__close-button"
        onClick={evt => (evt.nativeEvent.isCloseEvent = true)}
      />
    </div>
  );
};

Modal.Content = function({children}) {
  return <div className="Modal__content">{children}</div>;
};

Modal.Actions = function({children}) {
  return <div className="Modal__actions">{children}</div>;
};
