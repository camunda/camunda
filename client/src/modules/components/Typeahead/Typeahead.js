/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Input, Button, Icon, Dropdown} from 'components';
import OptionsList from './OptionsList';
import './Typeahead.scss';
import {t} from 'translation';
import classnames from 'classnames';

const defaultState = {
  query: '',
  open: false,
  selected: '',
};
export default class Typeahead extends React.Component {
  static defaultProps = {
    onSearch: () => {},
    onChange: () => {},
    onOpen: () => {},
    onClose: () => {},
  };

  state = defaultState;

  optionClicked = false;

  input = React.createRef();

  onTextChange = (evt) => {
    this.setState({query: evt.target.value, open: true});
    this.props.onSearch(evt.target.value);
  };

  componentDidMount() {
    const {initialValue, value} = this.props;

    this.findAndSelect(initialValue);
    this.findAndSelect(value);
  }

  componentDidUpdate(prevProps) {
    if (prevProps.value !== this.props.value) {
      if (typeof this.props.value === 'undefined') {
        this.setState(defaultState);
        this.props.onSearch('');
      } else {
        this.findAndSelect(this.props.value);
      }
    }
    if (!prevProps.initialValue && this.props.initialValue) {
      this.findAndSelect(this.props.initialValue);
    }
  }

  findAndSelect = (value) => {
    const {children, typedOption} = this.props;
    const foundOption = React.Children.toArray(children).find(
      (option) => option.props.value === value
    );

    if (typedOption) {
      this.setState({
        selected: value,
        query: value,
      });
    } else if (foundOption) {
      const {label, children} = foundOption.props;
      const selected = label || children;
      this.setState({
        selected,
        query: selected,
      });
    }
  };

  selectOption = ({props: {label, children, value}}) => {
    const selected = label || children;
    this.setState({
      selected,
      query: selected,
      open: false,
    });
    this.props.onChange(value);
    this.optionClicked = false;
  };

  getPlaceholderText = (isEmpty) => {
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

  open = () => {
    this.props.onOpen();
    this.setState({open: true, query: ''});
  };

  close = () => {
    this.props.onClose();
    this.setState(({selected}) => ({open: false, query: selected || ''}));
  };

  render() {
    const {children, disabled, loading, hasMore, async, typedOption, className} = this.props;
    const {query, open, selected} = this.state;
    const isEmpty = !loading && !query && !typedOption && React.Children.count(children) === 0;
    const isInputDisabled = isEmpty || disabled;

    return (
      <div className={classnames(className, 'Typeahead')}>
        <Input
          type="text"
          className={classnames('typeaheadInput', {selectionVisible: open && selected})}
          value={query}
          onFocus={this.open}
          onBlur={(evt) => {
            if (!this.optionClicked) {
              this.close();
            }
          }}
          onChange={this.onTextChange}
          ref={this.input}
          placeholder={this.getPlaceholderText(isEmpty)}
          disabled={isInputDisabled}
        />
        <Button
          tabIndex="-1"
          className="optionsButton"
          onClick={() => this.input.current.focus()}
          disabled={isInputDisabled}
        >
          <Icon type="down" className="downIcon" />
        </Button>
        <OptionsList
          async={async}
          open={open}
          onClose={this.close}
          onOpen={this.open}
          filter={query}
          onSelect={this.selectOption}
          input={this.input.current}
          onMouseDown={() => (this.optionClicked = true)}
          loading={loading}
          hasMore={hasMore}
          typedOption={typedOption}
        >
          {children}
        </OptionsList>
      </div>
    );
  }
}

Typeahead.Option = Dropdown.Option;

Typeahead.Highlight = function Highlight(props) {
  return props.children;
};

Typeahead.Highlight.propTypes = {
  children: (props) => {
    const option = React.Children.toArray(props.children);
    if (option.length !== 1 || typeof option[0] !== 'string') {
      return new Error('The Typeahed.Highlight should have only one string child element');
    }
  },
};
