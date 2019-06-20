/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Dropdown} from 'components';
import classnames from 'classnames';

const emptyLabel = 'Select...';

export default class Select extends React.Component {
  state = {
    selected: this.props.value,
    selectedLabel: emptyLabel
  };

  componentDidMount() {
    this.updateLabel();
  }

  componentDidUpdate({value}) {
    if (value !== this.props.value) {
      this.label = emptyLabel;
      this.setState({selected: this.props.value}, this.updateLabel);
    }
  }

  updateLabel = () => {
    if (this.label) {
      this.setState({selectedLabel: this.label});
    }
  };

  renderChildrenWithProps = (children, label) => {
    return React.Children.map(children, child => {
      const props = {};

      if (child && child.type === Select.Submenu) {
        props.checked = React.Children.toArray(child.props.children).some(
          child => child && child.props.value === this.state.selected
        );

        props.children = this.renderChildrenWithProps(child.props.children, child.props.label);
      } else {
        props.onClick = this.onChange;
        props.checked = child && child.props.value === this.state.selected;
        if (props.checked) {
          this.storeLabel(label, child.props.children);
        }
      }

      return child && React.cloneElement(child, props);
    });
  };

  storeLabel = (label, subLabel) => {
    if (label) {
      this.label = label + ' : ' + subLabel;
    } else {
      this.label = subLabel;
    }
  };

  onChange = evt => {
    const value = evt.target.getAttribute('value');
    if (value) {
      this.setState({selected: value});
      if (this.props.onChange) {
        this.props.onChange(value);
      }
    }
  };

  render() {
    return (
      <Dropdown
        {...this.props}
        label={this.state.selectedLabel}
        className={classnames('Select', this.props.className)}
      >
        {this.renderChildrenWithProps(this.props.children)}
      </Dropdown>
    );
  }
}

Select.Option = function Option(props) {
  return <Dropdown.Option {...props}>{props.children}</Dropdown.Option>;
};

Select.Submenu = function Submenu(props) {
  return <Dropdown.Submenu {...props}>{props.children}</Dropdown.Submenu>;
};
