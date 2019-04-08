/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import {Input, Dropdown} from 'components';
import {formatters} from 'services';

import './Typeahead.scss';

const valuesShownInBox = 10;
const valueHeight = 30;

export default class Typeahead extends React.Component {
  state = {
    query: '',
    optionsVisible: false,
    selectedValueIdx: 0,
    firstShownOptionIdx: 0,
    lastCommittedValue: ''
  };

  componentDidMount() {
    document.body.addEventListener('click', this.close);
  }

  componentDidUpdate(prevProps) {
    if (this.props.initialValue && this.props.initialValue !== prevProps.initialValue) {
      const value = this.getFormatter()(this.props.initialValue);
      this.setState({
        query: value,
        lastCommittedValue: value
      });
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
    }
  };

  close = evt => {
    if (!evt || !this.container.contains(evt.target)) {
      this.setState({
        optionsVisible: false
      });
    }
  };

  updateValues = () => {
    this.setState({
      selectedValueIdx: 0,
      firstShownOptionIdx: 0
    });
  };

  updateQuery = async evt => {
    this.setState({query: evt.target.value});
    this.showOptions();
    this.updateValues();
  };

  getFormatter = () => this.props.formatter || (v => v);

  selectValue = value => () => {
    const formatter = this.getFormatter();
    const formattedValue = formatter(value);
    this.setState({
      query: formattedValue,
      lastCommittedValue: formattedValue,
      selectedValueIdx: 0,
      firstShownOptionIdx: 0,
      optionsVisible: false
    });

    if (this.props.onSelect) {
      this.props.onSelect(value);
    }
    this.close();
  };

  resetToLastCommitted = () => {
    this.setState({query: this.state.lastCommittedValue});
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

      const firstShownOptionIdx = this.scrollIntoView(selectedValueIdx);

      this.setState({
        selectedValueIdx,
        firstShownOptionIdx
      });
    }
  };

  scrollIntoView = selectedValueIdx => {
    let {firstShownOptionIdx} = this.state;
    const values = this.getFilteredValues();

    if (this.optionsList) {
      if (selectedValueIdx === 0) {
        this.optionsList.scrollTop = 0;
        firstShownOptionIdx = 0;
      }

      if (selectedValueIdx === values.length - 1) {
        this.optionsList.scrollTop = this.optionsList.scrollHeight;
        firstShownOptionIdx = values.length - valuesShownInBox;
      }

      if (selectedValueIdx >= firstShownOptionIdx + valuesShownInBox) {
        this.optionsList.scrollTop = (firstShownOptionIdx + 1) * valueHeight;
        firstShownOptionIdx++;
      }

      if (selectedValueIdx < firstShownOptionIdx) {
        firstShownOptionIdx--;
        this.optionsList.scrollTop = selectedValueIdx * valueHeight;
      }
    }

    return firstShownOptionIdx;
  };

  optionsListRef = optionsList => {
    this.optionsList = optionsList;
    if (optionsList) {
      this.optionsList.highestWidth = this.optionsList.offsetWidth;
    }
  };

  containerRef = container => {
    this.container = container;
  };

  inputRef = input => {
    this.input = input;
  };

  getFilteredValues = () =>
    this.props.values.filter(value =>
      this.getFormatter()(value)
        .toLowerCase()
        .includes(this.state.query.toLowerCase())
    );

  render() {
    const formatter = this.getFormatter();

    const {query, selectedValueIdx, optionsVisible} = this.state;
    const values = this.getFilteredValues();

    const searchResultContainerStyle = {
      maxHeight: valueHeight * valuesShownInBox + 'px',
      maxWidth: this.optionsList && this.optionsList.highestWidth + 'px'
    };

    const valueStyle = {
      height: valueHeight + 'px',
      minWidth: this.input && this.input.clientWidth + 'px'
    };

    const hasNoValues = this.props.values.length === 0;

    return (
      <div ref={this.containerRef} className={classnames('Typeahead', this.props.className)}>
        <Input
          className={classnames({isInvalid: this.props.isInvalid})}
          value={hasNoValues ? 'No results found' : query}
          onChange={this.updateQuery}
          onClick={this.showOptions}
          onFocus={this.showOptions}
          onKeyDown={this.handleKeyPress}
          onBlur={this.resetToLastCommitted}
          placeholder={this.props.placeholder}
          ref={this.inputRef}
          disabled={this.props.disabled || hasNoValues}
        />
        {optionsVisible && values.length > 0 && (
          <div
            style={searchResultContainerStyle}
            className="searchResultsList"
            ref={this.optionsListRef}
            onMouseUp={this.returnFocusToInput}
          >
            {values.map((value, index) => {
              return (
                <Dropdown.Option
                  className={classnames('searchResult', {
                    isActive: index === selectedValueIdx
                  })}
                  style={valueStyle}
                  onMouseDown={evt => evt.preventDefault()}
                  onClick={this.selectValue(value)}
                  key={value && value.id ? value.id : formatter(value)}
                >
                  {formatters.getHighlightedText(formatter(value), this.state.query)}
                </Dropdown.Option>
              );
            })}
          </div>
        )}
      </div>
    );
  }
}
