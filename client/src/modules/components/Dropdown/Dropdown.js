import React from 'react';

import {Button} from 'components';
import DropdownOption from './DropdownOption';

import './Dropdown.css';

export default class Dropdown extends React.Component {
  constructor(props) {
    super(props);
    this.options = [];
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
    document.body.addEventListener('click', this.close, true);
  }

  handleKeyPress = evt => {
   if(evt.key !== 'Tab') {
     evt.preventDefault();
   }
   if(evt.key === 'Escape') {
     this.close({});
   } else {
     const dropdownButton = this.container.children[0];
     const options = this.options;

     if(options[0] !== dropdownButton) {
       options.unshift(dropdownButton);
     }

     evt = evt || window.event;
     let selectedOption = options.indexOf(document.activeElement);

     if( (evt.key === 'ArrowDown')) {
       selectedOption++;
       selectedOption = Math.min(selectedOption, options.length-1);
     }

     if( (evt.key === 'ArrowUp')) {
       selectedOption --;
       selectedOption = Math.max(selectedOption, 0);
     }
     options[selectedOption].focus();
   }
 }

  render() {


    return (<div {...this.props} className={'Dropdown ' + (this.state.open ? 'is-open' : '') + (this.props.className ? ' ' + this.props.className : '')} ref={this.storeContainer} onClick={this.toggleOpen} onKeyDown={this.handleKeyPress}>
      <Button className="Dropdown__button" aria-haspopup="true" aria-expanded={this.state.open ? "true" : "false"} id={this.props.id ? this.props.id + '-button' : ''}>{this.props.label} <span className='Dropdown__caret' /></Button>
      <div className="Dropdown__menu" aria-labelledby={this.props.id ? this.props.id + '-button' : ''}>
        <ul className="Dropdown__menu-list">
          {React.Children.map(this.props.children,
            (child, idx) => <li ref={this.optionRef} key={idx}>{child}</li> 
          )}
        </ul>
      </div>
    </div>);
  }



  optionRef = option => {
    if(option) {
      this.options.push(option.children[0]);
    }
  }

  storeContainer = node => {
    this.container = node;
  }

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close, true);
  }
}

Dropdown.Option = DropdownOption;
