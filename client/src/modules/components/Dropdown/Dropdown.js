import React from 'react';

import {Button} from 'components';
import DropdownOption from './DropdownOption';

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
    return (<div className={'Dropdown ' + (this.state.open ? 'is-open' : '')} ref={this.storeContainer} onClick={this.toggleOpen}>
      <Button className="Dropdown__button" aria-haspopup="true" aria-expanded={this.state.open ? "true" : "false"} id={this.props.id}>{this.props.label} <span className='Dropdown__caret' /></Button>
      <div className="Dropdown__menu" aria-labelledby={this.props.id}>
        <ul className="Dropdown__menu-list">
          {React.Children.map(this.props.children,
            (child, idx) => <li key={idx}>{child}</li>
          )}
        </ul>
      </div>
    </div>);
  }

  storeContainer = node => {
    this.container = node;
  }

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close, true);
  }
}

Dropdown.Option = DropdownOption;
