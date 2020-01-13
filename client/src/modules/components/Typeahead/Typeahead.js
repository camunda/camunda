/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Input, Button, Icon, Dropdown} from 'components';
import OptionsList from './OptionsList';
import './Typeahead.scss';
import {t} from 'translation';

export default class Typeahead extends React.Component {
  static defaultProps = {
    onSearch: () => {},
    onChange: () => {}
  };

  state = {
    query: '',
    open: false,
    selected: ''
  };

  optionClicked = false;

  input = React.createRef();

  onTextChange = evt => {
    this.setState({query: evt.target.value, open: true});
    this.props.onSearch(evt.target.value);
  };

  showOptions = () => {
    this.setState({open: true});
    this.input.current.focus();
  };

  componentDidMount() {
    const {children, initialValue} = this.props;
    if (initialValue) {
      const {props} = React.Children.toArray(children).find(
        option => option.props.value === initialValue
      );

      const selected = props.label || props.children;
      this.setState({
        selected,
        query: selected
      });
    }
  }

  selectOption = ({props: {label, children, value}}) => {
    const selected = label || children;
    this.setState({
      selected,
      query: selected,
      open: false
    });
    this.props.onChange(value);
    this.optionClicked = false;
  };

  getPlaceholderText = isEmpty => {
    const {placeholder, disabled, noValuesMessage} = this.props;
    const {selected} = this.state;
    if (selected) {
      return selected;
    }
    if (disabled || isEmpty) {
      return noValuesMessage || t('common.notFound');
    }
    return placeholder;
  };

  open = () => this.setState({open: true, query: ''});
  close = () => this.setState(({selected}) => ({open: false, query: selected || ''}));

  render() {
    const {children, disabled} = this.props;
    const {query, open} = this.state;
    const isEmpty = React.Children.toArray(children).length === 0;

    return (
      <div className="Typeahead">
        <Input
          type="text"
          className="typeaheadInput"
          value={query}
          onFocus={this.open}
          onBlur={evt => {
            if (!this.optionClicked) {
              this.close();
            }
          }}
          onChange={this.onTextChange}
          ref={this.input}
          placeholder={this.getPlaceholderText(isEmpty)}
          disabled={isEmpty || disabled}
          onClear={this.open}
        />
        <Button className="optionsButton" onClick={this.showOptions} disabled={disabled}>
          <Icon type="down" className="downIcon" />
        </Button>
        <OptionsList
          open={open}
          onClose={this.close}
          onOpen={this.open}
          filter={query}
          onSelect={this.selectOption}
          input={this.input.current}
          onMouseDown={() => (this.optionClicked = true)}
        >
          {children}
        </OptionsList>
      </div>
    );
  }
}

Typeahead.Option = Dropdown.Option;
