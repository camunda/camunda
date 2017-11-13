import React from 'react';

import {Button} from 'components';

import './Dropdown.css';

export default class Dropdown extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      open: false
    };
  }

  toggleOpen = () => {
    this.setState({open: !this.state.open});
  }

  close = ({target}) => {
    if(!this.container.contains(target)) {
      this.setState({open: false});
    }
  }

  componentDidMount() {
    document.body.addEventListener('click', this.close, true);
  }

  render() {
    return (<div className='Dropdown' ref={this.storeContainer} onClick={this.toggleOpen}>
      <Button>{this.props.label} <span className='Dropdown__caret' /></Button>
      {
        this.state.open &&
        <ul>
          {React.Children.map(this.props.children,
            (child, idx) => <li key={idx}>{child}</li>
          )}
        </ul>
      }
    </div>);
  }

  storeContainer = node => {
    this.container = node;
  }

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close, true);
  }
}

Dropdown.Option = Button;
