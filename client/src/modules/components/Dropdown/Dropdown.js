import React from 'react';

import {Button} from 'components';
import DropdownOption from './DropdownOption';

import './Dropdown.css';

export default class Dropdown extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      open: false,
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
    const options = Array.from(document.getElementsByClassName('DropdownOption'));
    const dropdownButton = document.getElementsByClassName('Dropdown__button')[0];

    document.body.addEventListener('click', this.close, true);

    document.onkeydown = (evt) => {
      evt = evt || window.event;
      let selectedOption = options.indexOf(document.activeElement);

      if((this.state.open === true) && (evt.keyCode === 40)) {
        if(selectedOption === -1) {
          options[0].focus();
          selectedOption = options.indexOf(document.activeElement);
        } else if (selectedOption < options.length - 1) {
          options[selectedOption + 1].focus();
          selectedOption = options.indexOf(document.activeElement);
        }
      }

      if((this.state.open === true) && (evt.keyCode === 38) && (selectedOption !== -1)) {
        if(selectedOption === 0) {
          dropdownButton.focus();
        } else {
          options[selectedOption - 1].focus();
          selectedOption = options.indexOf(document.activeElement);
        }
      }

      if(evt.keyCode === 27) {
        this.close({});
      }

    };
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
