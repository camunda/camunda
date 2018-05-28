import React from 'react';
import classnames from 'classnames';
import {Input, Dropdown} from 'components';

import './Typeahead.css';

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
    this.loadVariables();
  }

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close);
  }

  showOptions = () => {
    this.setState({
      optionsVisible: true
    });
  };

  close = evt => {
    if (!evt || !this.container.contains(evt.target)) {
      this.setState({
        optionsVisible: false
      });
    }
  };

  loadVariables = async () => {
    this.setState({
      query: '',
      values: await this.props.getValues('')
    });
  };

  updateQuery = async evt => {
    const query = evt.target.value;
    this.setState({
      query,
      values: await this.props.getValues(query),
      selectedValueIdx: 0,
      firstShownOptionIdx: 0
    });
  };

  selectValue = value => _ => {
    this.setState({
      values: [],
      query: this.props.nameRenderer(value),
      selectedValueIdx: 0,
      firstShownOptionIdx: 0
    });
    this.props.selectValue(value);
    this.close();
  };

  handleKeyPress = evt => {
    const {values, selectedValueIdx} = this.state;
    if (evt.key === 'Enter') {
      evt.preventDefault();

      this.selectValue(values[selectedValueIdx])();
      return;
    }

    if (evt.key === 'Escape') {
      evt.stopPropagation();
      this.close();
    } else {
      const options = this.state.values;

      evt = evt || window.event;
      let selectedValueIdx = this.state.selectedValueIdx;

      if (evt.key === 'ArrowDown') {
        evt.preventDefault();
        if (!this.state.optionsVisible) {
          this.showOptions();
        } else {
          selectedValueIdx++;
          selectedValueIdx = Math.min(selectedValueIdx, options.length - 1);
        }
      }

      if (evt.key === 'ArrowUp') {
        evt.preventDefault();
        selectedValueIdx--;
        selectedValueIdx = Math.max(selectedValueIdx, 0);
      }
      this.setState(
        {
          selectedValueIdx
        },
        () => {
          if (this.optionsList) {
            let {firstShownOptionIdx} = this.state;
            if (selectedValueIdx >= firstShownOptionIdx + 10) {
              this.optionsList.scrollTo(0, (firstShownOptionIdx + 1) * 30);
              firstShownOptionIdx++;
              this.setState({
                firstShownOptionIdx
              });
            }
            if (selectedValueIdx < firstShownOptionIdx) {
              this.optionsList.scrollTo(0, selectedValueIdx * 30);
              firstShownOptionIdx--;
              this.setState({
                firstShownOptionIdx
              });
            }
          }
        }
      );
    }
  };

  optionsListRef = optionsList => {
    this.optionsList = optionsList;
  };

  containerRef = container => {
    this.container = container;
  };

  render() {
    const {query, values, selectedValueIdx, optionsVisible} = this.state;

    return (
      <div ref={this.containerRef} className="Typeahead">
        <Input
          className="Typeahead__input"
          value={query}
          onChange={this.updateQuery}
          onClick={this.showOptions}
          onKeyDown={this.handleKeyPress}
        />
        {optionsVisible &&
          values.length > 0 && (
            <div className="Typeahead__search-result-list" ref={this.optionsListRef}>
              {values.map((value, index) => {
                return (
                  <Dropdown.Option
                    className={classnames('Typeahead__search-result', {
                      'is-active': index === selectedValueIdx
                    })}
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
