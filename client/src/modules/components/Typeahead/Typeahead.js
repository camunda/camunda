/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import {Input, Dropdown, Icon, Button, LoadingIndicator} from 'components';
import {formatters} from 'services';
import debounce from 'debounce';

import './Typeahead.scss';
import {t} from 'translation';

export default class Typeahead extends React.Component {
  state = {
    query: '',
    optionsVisible: false,
    selectedValueIdx: -1,
    lastCommittedValue: '',
    values: [],
    loading: false,
    hasData: true,
    total: 0
  };

  componentDidMount() {
    document.body.addEventListener('click', this.close);
    this.loadInitialValues();
  }

  isAsync = () => typeof this.props.values === 'function';

  loadInitialValues = async () => {
    const {initialValue, values} = this.props;

    const query = initialValue ? this.format(initialValue).text : '';

    let newValues = values;
    let total = values.length;

    if (this.isAsync()) {
      const valuesData = await values(query);
      newValues = valuesData.result;
      total = valuesData.total;
    }

    this.setState({
      query,
      lastCommittedValue: query,
      hasData: newValues.length > 0,
      values: newValues,
      total: total
    });
  };

  componentDidUpdate(prevProps) {
    if (
      (this.props.initialValue && this.props.initialValue !== prevProps.initialValue) ||
      this.props.values !== prevProps.values
    ) {
      this.loadInitialValues();
    }
    if (this.props.disabled !== prevProps.disabled) {
      this.setState({
        query: this.props.disabled ? this.props.placeholder : '',
        lastCommittedValue: ''
      });
    }
  }

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close);
  }

  returnFocusToInput = evt => {
    if (evt.target === this.optionsList) {
      this.input.focus();
    }
  };

  showOptions = evt => {
    if (evt && evt.type === 'click') {
      this.input.select();
    }

    if (!this.state.optionsVisible) {
      this.setState({
        optionsVisible: true
      });

      if (this.isAsync() && !this.state.values.length) {
        this.updateQuery(this.state.query);
      }
    }
  };

  close = evt => {
    if (!evt || !this.container.contains(evt.target)) {
      this.setState({
        optionsVisible: false
      });
    }
  };

  updateQuery = async query => {
    this.setState({query, selectedValueIdx: -1});
    if (this.isAsync()) {
      this.setState({loading: true});
      await this.loadNewValues(query);
    }
    this.showOptions();
  };

  loadNewValues = debounce(async query => {
    const {result, total} = await this.props.values(query);
    this.setState({values: result, total, loading: false});
  }, 500);

  format = value => {
    const {formatter} = this.props;
    if (!formatter) {
      return {text: value};
    }
    const formatted = this.props.formatter(value);
    if (typeof formatted === 'string') {
      return {text: formatted};
    }
    return formatted;
  };

  selectValue = value => () => {
    const formattedValue = this.format(value).text;
    this.setState({
      query: formattedValue,
      lastCommittedValue: formattedValue,
      selectedValueIdx: -1,
      optionsVisible: false
    });

    if (this.props.onSelect) {
      this.props.onSelect(value);
    }
    this.close();
  };

  handleKeyPress = evt => {
    evt = evt || window.event;

    if (evt.key === 'Tab') {
      this.close();
      return;
    }

    const {selectedValueIdx, optionsVisible} = this.state;
    const values = this.getFilteredValues();
    if (evt.key === 'Enter') {
      evt.preventDefault();
      if (optionsVisible && values[selectedValueIdx]) {
        this.selectValue(values[selectedValueIdx])();
        evt.stopPropagation();
      }
      return;
    }

    if (evt.key === 'Escape') {
      if (this.state.optionsVisible) {
        evt.stopPropagation();
      }
      this.close();
    } else {
      let selectedValueIdx = this.state.selectedValueIdx;

      if (evt.key === 'ArrowDown') {
        evt.preventDefault();
        if (!this.state.optionsVisible) {
          this.showOptions();
        } else {
          selectedValueIdx = (selectedValueIdx + 1) % values.length;
        }
      }

      if (evt.key === 'ArrowUp') {
        evt.preventDefault();
        selectedValueIdx = selectedValueIdx - 1 < 0 ? values.length - 1 : selectedValueIdx - 1;
      }

      if (this.optionsList) {
        const selectedItem = this.optionsList.querySelectorAll('.searchResult')[selectedValueIdx];
        if (selectedItem) {
          selectedItem.scrollIntoView({block: 'nearest', inline: 'nearest'});
        }

        // scroll to end on the last element to show the info message
        if (selectedValueIdx === values.length - 1) {
          this.optionsList.scrollTop = this.optionsList.scrollHeight;
        }
      }

      this.setState({
        selectedValueIdx
      });
    }
  };

  renderOption = (value, index) => {
    const {query, selectedValueIdx} = this.state;
    const {text, tag, subTexts} = this.format(value);

    return (
      <Dropdown.Option
        className={classnames('searchResult', {
          isActive: index === selectedValueIdx
        })}
        onMouseDown={evt => evt.preventDefault()}
        onClick={this.selectValue(value)}
        key={value && value.id ? value.id : text}
      >
        {formatters.getHighlightedText(text, query)}
        {tag}
        {subTexts && (
          <span className="subTexts">
            {subTexts
              .filter(text => text)
              .map((text, i) => (
                <span className="subText" key={i}>
                  {formatters.getHighlightedText(text, query, true)}
                </span>
              ))}
          </span>
        )}
      </Dropdown.Option>
    );
  };

  optionsListRef = optionsList => {
    this.optionsList = optionsList;
  };

  containerRef = container => {
    this.container = container;
  };

  inputRef = input => {
    this.input = input;
  };

  getFilteredValues = () => {
    const {values, query} = this.state;
    // we do not filter the values for this case because they are filtered by the backend
    if (this.isAsync()) {
      return values;
    }

    return values.filter(value =>
      this.format(value)
        .text.toLowerCase()
        .includes(query.toLowerCase())
    );
  };

  resetToLastCommitted = () => {
    this.setState({query: this.state.lastCommittedValue, optionsVisible: false});
  };

  render() {
    const {query, optionsVisible, loading, hasData, total} = this.state;
    const values = this.getFilteredValues();
    const noValuesMessage = this.props.noValuesMessage || t('common.notFound');

    const messageOption = message =>
      !loading && <Dropdown.Option className="searchResult message">{message}</Dropdown.Option>;

    return (
      <div ref={this.containerRef} className={classnames('Typeahead', this.props.className)}>
        <Input
          className={classnames('typeaheadInput', {isInvalid: this.props.isInvalid})}
          value={hasData ? query : noValuesMessage}
          onChange={({target: {value}}) => this.updateQuery(value)}
          onClick={this.showOptions}
          onFocus={this.showOptions}
          onKeyDown={this.handleKeyPress}
          onBlur={this.resetToLastCommitted}
          placeholder={this.props.placeholder}
          ref={this.inputRef}
          disabled={this.props.disabled || !hasData}
          onClear={() => this.updateQuery('')}
        />
        <Button
          className="optionsButton"
          onClick={this.showOptions}
          disabled={this.props.disabled || !hasData}
        >
          <Icon type="down" className="downIcon" />
        </Button>
        {optionsVisible && hasData && (
          <div
            className="searchResultsList"
            ref={this.optionsListRef}
            onMouseUp={this.returnFocusToInput}
          >
            {loading ? <LoadingIndicator /> : values.map(this.renderOption)}
            {this.isAsync() &&
              total > values.length &&
              messageOption(t('common.searchForMore', {count: 25}))}
            {values.length <= 0 && messageOption(t('common.notFound'))}
          </div>
        )}
      </div>
    );
  }
}
