import React from 'react';
import classnames from 'classnames';

import {Button, Icon} from 'components';
import DropdownOption from './DropdownOption';
import Submenu from './Submenu';

import './Dropdown.css';

export default class Dropdown extends React.Component {
  menuContainer = React.createRef();

  constructor(props) {
    super(props);
    this.options = [];
    this.state = {
      open: false,
      openSubmenu: null
    };
  }

  toggleOpen = () => {
    this.setState({open: !this.state.open, openSubmenu: null});
  };

  close = ({target}) => {
    if (!this.container.contains(target)) {
      this.setState({open: false, openSubmenu: null});
    }
  };

  componentDidMount() {
    document.body.addEventListener('click', this.close, true);
  }

  handleKeyPress = evt => {
    if (evt.key !== 'Tab') {
      evt.preventDefault();
    }

    if (evt.key === 'Enter') {
      evt.target.click();
    }

    if (evt.key === 'Escape') {
      this.close({});
    } else if (evt.key === 'ArrowDown' || evt.key === 'ArrowUp') {
      const dropdownButton = this.container.children[0];

      const options = this.options.filter(option => {
        return !option.disabled;
      });

      if (options[0] !== dropdownButton) {
        options.unshift(dropdownButton);
      }

      evt = evt || window.event;
      let selectedOption = options.indexOf(document.activeElement);

      if (evt.key === 'ArrowDown') {
        if (!this.state.open) {
          this.toggleOpen();
        } else {
          selectedOption++;
          selectedOption = Math.min(selectedOption, options.length - 1);
        }
      }

      if (evt.key === 'ArrowUp') {
        selectedOption--;
        selectedOption = Math.max(selectedOption, 0);
      }
      options[selectedOption].focus();
    }
  };

  render() {
    const {open} = this.state;

    return (
      <div
        id={this.props.id}
        className={classnames(this.props.className, 'Dropdown', {
          'is-open': open
        })}
        ref={this.storeContainer}
        onClick={this.toggleOpen}
        onKeyDown={this.handleKeyPress}
      >
        <Button
          className="Dropdown__button"
          aria-haspopup="true"
          aria-expanded={open ? 'true' : 'false'}
          active={this.props.active}
          disabled={this.props.disabled}
          id={this.props.id ? this.props.id + '-button' : ''}
        >
          {this.props.label}
          <Icon type="down" />
        </Button>
        <div
          className="Dropdown__menu"
          aria-labelledby={this.props.id ? this.props.id + '-button' : ''}
          ref={this.menuContainer}
          style={{minWidth: (this.container && this.container.clientWidth) + 'px'}}
        >
          <ul className="Dropdown__menu-list">
            {React.Children.map(this.props.children, (child, idx) => (
              <li ref={this.optionRef} key={idx}>
                {child.type === Submenu
                  ? React.cloneElement(child, {
                      open: this.state.openSubmenu === idx,
                      offset: this.menuContainer.current && this.menuContainer.current.offsetWidth,
                      onOpen: evt => {
                        evt.stopPropagation();
                        this.setState({openSubmenu: idx});
                      }
                    })
                  : child}
              </li>
            ))}
          </ul>
        </div>
      </div>
    );
  }

  optionRef = option => {
    if (option) {
      this.options.push(option.children[0]);
    }
  };

  storeContainer = node => {
    this.container = node;
  };

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close, true);
  }
}

Dropdown.Option = DropdownOption;
Dropdown.Submenu = Submenu;
