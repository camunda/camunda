import React from 'react';

import {Button} from 'components';

import './Popover.css';

export default class Popover extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      open: false,
      calcStyles: {}
    };
  }

  componentDidMount() {
    this.mounted = true;
    document.body.addEventListener('click', this.close);
    new MutationObserver(this.getDialogStyle).observe(this.popoverRoot, {
      childList: true,
      subtree: true
    });
  }

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close);
    this.mounted = false;
  }

  toggleOpen = evt => {
    evt.preventDefault();

    this.setState({
      open: !this.state.open
    });
  };

  close = evt => {
    // We need to wait for the event delegation to be finished
    // so we know whether the click occured inside the popover,
    // in which case we do not want to close the popover
    setTimeout(() => {
      if (!evt.inOverlay && this.mounted) {
        this.setState({
          open: false
        });
      }
    });
  };

  getDialogStyle = () => {
    let style = {};
    if (this.buttonRef && this.popoverDialogRef) {
      const overlayWidth = this.popoverDialogRef.clientWidth;
      const buttonPosition = this.buttonRef.getBoundingClientRect().x;

      const bodyWidth = document.body.clientWidth;

      if (buttonPosition + overlayWidth > bodyWidth) {
        style.right = 0;
      } else {
        style.left = 0;
      }
    }

    this.setState({
      calcStyles: style
    });
  };

  createOverlay = () => {
    return (
      <div>
        <span className="Popover__dialog-arrow-border"> </span>
        <span className="Popover__dialog-arrow" />
        <div
          ref={node => {
            this.popoverDialogRef = node;
          }}
          style={this.state.calcStyles}
          className="Popover__dialog"
        >
          {this.props.children}{' '}
        </div>
      </div>
    );
  };

  storeButtonRef = ref => {
    this.buttonRef = ref;
  };

  catchClick = evt => {
    evt.nativeEvent.inOverlay = true;
  };

  render() {
    return (
      <div
        ref={node => {
          this.popoverRoot = node;
        }}
        className={'Popover ' + (this.props.className || '')}
        onClick={this.catchClick}
      >
        <Button
          onClick={this.toggleOpen}
          reference={this.storeButtonRef}
          className={'Popover__button' + (this.state.open ? '--open' : '')}
        >
          {this.props.title}
        </Button>
        {this.state.open && this.createOverlay()}
      </div>
    );
  }
}
