import React from 'react';
import classnames from 'classnames';
import debounce from 'debounce';
import {Input, Dropdown} from 'components';

import './Typeahead.css';

const valuesShownInBox = 10;
const valueHeight = 30;

export default class Typeahead extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      values: [],
      query: '',
      optionsVisible: false,
      selectedValueIdx: 0,
      firstShownOptionIdx: 0
    };
  }

  componentDidMount() {
    document.body.addEventListener('click', this.close);
    this.loadValues();
  }

  componentDidUpdate(prevProps) {
    if (this.props.initialValue && this.props.initialValue !== prevProps.initialValue) {
      this.setState(
        {
          query: this.props.initialValue
        },
        () => {
          this.loadValues();
        }
      );
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

    if (!this.state.optionsVisible && this.state.values) {
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

  loadValues = async () => {
    this.setState({
      values: await this.props.getValues(this.state.query)
    });
  };

  updateValues = debounce(async () => {
    this.setState({
      // Asynchronously request new values
      values: await this.props.getValues(this.state.query),
      selectedValueIdx: 0,
      firstShownOptionIdx: 0
    });
  }, 300);

  updateQuery = async evt => {
    const query = evt.target.value;
    this.setState({
      // Synchronously update the input text
      query
    });
    this.showOptions();
    this.updateValues();
  };

  selectValue = value => _ => {
    this.setState({
      values: [],
      query: this.props.nameRenderer(value),
      selectedValueIdx: 0,
      firstShownOptionIdx: 0,
      optionsVisible: false
    });
    this.props.selectValue(value);
    this.close();
  };

  handleKeyPress = evt => {
    evt = evt || window.event;

    if (evt.key === 'Tab') {
      this.close();
      return;
    }

    const {values, selectedValueIdx, optionsVisible} = this.state;
    if (evt.key === 'Enter') {
      evt.preventDefault();
      if (optionsVisible && values[selectedValueIdx]) {
        this.selectValue(values[selectedValueIdx])();
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
    let {firstShownOptionIdx, values} = this.state;

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

  render() {
    const {query, values, selectedValueIdx, optionsVisible} = this.state;

    const searchResultContainerStyle = {
      maxHeight: valueHeight * valuesShownInBox + 'px',
      maxWidth: this.optionsList && this.optionsList.highestWidth + 'px'
    };

    const valueStyle = {
      height: valueHeight + 'px'
    };

    return (
      <div ref={this.containerRef} className="Typeahead">
        <Input
          className="Typeahead__input"
          value={query}
          onChange={this.updateQuery}
          onClick={this.showOptions}
          onKeyDown={this.handleKeyPress}
          ref={this.inputRef}
        />
        {optionsVisible &&
          values.length > 0 && (
            <div
              style={searchResultContainerStyle}
              className="Typeahead__search-result-list"
              ref={this.optionsListRef}
              onMouseUp={this.returnFocusToInput}
            >
              {values.map((value, index) => {
                return (
                  <Dropdown.Option
                    className={classnames('Typeahead__search-result', {
                      'is-active': index === selectedValueIdx
                    })}
                    style={valueStyle}
                    onClick={this.selectValue(value)}
                    key={this.props.nameRenderer(value)}
                  >
                    {this.props.nameRenderer(value)}
                  </Dropdown.Option>
                );
              })}
            </div>
          )}
      </div>
    );
  }
}
