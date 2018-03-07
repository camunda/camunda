import React from 'react';

import {Button} from 'components';

import './Popover.css';

export default class Popover extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      open: false
    };
  }

  componentDidMount() {
    this.mounted = true;
    document.body.addEventListener('click', this.close);
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

  getButtonWidth = () => {
    return this.buttonRef && this.buttonRef.offsetWidth;
  };

  createOverlay = () => {
    const arrowOffset = this.getButtonWidth() / 2;
    return (
      <div className="Popover__dialog">
        <span
          className="Popover__dialog-arrow-border"
          style={{
            left: arrowOffset + 'px'
          }}
        >
          {' '}
        </span>
        {this.props.children}
        <span
          className="Popover__dialog-arrow"
          style={{
            left: arrowOffset + 'px'
          }}
        >
          {' '}
        </span>
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
      <div className={'Popover ' + (this.props.className || '')} onClick={this.catchClick}>
        <Button
          onClick={this.toggleOpen}
          reference={this.storeButtonRef}
          className="Popover__button"
        >
          {this.props.title}
        </Button>
        {this.state.open && this.createOverlay()}
      </div>
    );
  }
}
