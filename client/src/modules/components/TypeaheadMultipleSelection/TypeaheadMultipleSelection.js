import React from 'react';
import {Input, LoadingIndicator} from 'components';
import {formatters} from 'services';

import './TypeaheadMultipleSelection.css';

export default class TypeaheadMultipleSelection extends React.Component {
  state = {
    searchQuery: ''
  };
  mapSelectedValues = values => {
    return (
      values.length > 0 && (
        <div className="TypeaheadMultipleSelection__labeled-valueList">
          <p>Selected Values: </p>
          <div className="TypeaheadMultipleSelection__values-sublist">
            {values.map((value, idx) => {
              return (
                <li key={idx} className="TypeaheadMultipleSelection__valueListItem">
                  <label>
                    <Input
                      type="checkbox"
                      checked
                      value={value}
                      onChange={this.props.toggleValue}
                    />
                    {value}
                  </label>
                </li>
              );
            })}
          </div>
        </div>
      )
    );
  };

  mapAvaliableValues = (availableValues, selectedValues) => {
    return (
      <div className="TypeaheadMultipleSelection__labeled-valueList">
        <p>Available Values: </p>
        <div className="TypeaheadMultipleSelection__values-sublist">
          {availableValues.map((value, idx) => {
            if (!selectedValues.includes(value)) {
              return (
                <li key={idx} className="TypeaheadMultipleSelection__valueListItem">
                  <label>
                    <Input
                      type="checkbox"
                      checked={selectedValues.includes(value)}
                      value={value}
                      onChange={this.props.toggleValue}
                    />
                    {formatters.getHighlightedText(value, this.state.searchQuery)}
                  </label>
                </li>
              );
            }
            return null;
          })}
        </div>
      </div>
    );
  };

  render() {
    const {availableValues, selectedValues, loading} = this.props;
    const input = (
      <div className="TypeaheadMultipleSelection__labeled-input">
        <p>Values that contain:</p>
        <Input
          className="TypeaheadMultipleSelection__input"
          onChange={e => {
            this.setState({searchQuery: e.target.value});
            return this.props.setFilter(e);
          }}
        />
      </div>
    );
    const loadingIndicator = loading ? <LoadingIndicator /> : '';
    if (availableValues.length === 0) {
      return (
        <div className="TypeaheadMultipleSelection">
          {input}
          {loadingIndicator}
          <div className="TypeaheadMultipleSelection__valueList">
            {this.mapSelectedValues(selectedValues)}
            <li className="TypeaheadMultipleSelection__no-items">
              {loading ? '' : 'No values match the query'}
            </li>
          </div>
        </div>
      );
    }
    return (
      <div className="TypeaheadMultipleSelection">
        {input}
        {loadingIndicator}
        <div className="TypeaheadMultipleSelection__valueList">
          {this.mapSelectedValues(selectedValues)}
          {this.mapAvaliableValues(availableValues, selectedValues)}
        </div>
      </div>
    );
  }
}
