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
          <p>Selected {this.props.label}: </p>
          <div className="TypeaheadMultipleSelection__values-sublist">
            {values.map((value, idx) => {
              return (
                <li key={idx} className="TypeaheadMultipleSelection__valueListItem">
                  <label>
                    <Input type="checkbox" checked value={idx} onChange={this.toggleSelected} />
                    {this.props.format(value)}
                  </label>
                </li>
              );
            })}
          </div>
        </div>
      )
    );
  };

  toggleSelected = ({target: {value, checked}}) =>
    this.props.toggleValue(this.props.selectedValues[value], checked);

  mapAvaliableValues = (availableValues, selectedValues) => {
    return (
      <div className="TypeaheadMultipleSelection__labeled-valueList">
        <p>Available {this.props.label}: </p>
        <div className="TypeaheadMultipleSelection__values-sublist">
          {availableValues.map((value, idx) => {
            if (!selectedValues.includes(value)) {
              return (
                <li key={idx} className="TypeaheadMultipleSelection__valueListItem">
                  <label>
                    <Input
                      type="checkbox"
                      checked={selectedValues.includes(value)}
                      value={idx}
                      onChange={this.toggleAvailable}
                    />
                    {formatters.getHighlightedText(
                      this.props.format(value),
                      this.state.searchQuery
                    )}
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

  toggleAvailable = ({target: {value, checked}}) =>
    this.props.toggleValue(this.props.availableValues[value], checked);

  render() {
    const {availableValues, selectedValues, loading} = this.props;
    const input = (
      <div className="TypeaheadMultipleSelection__labeled-input">
        <Input
          className="TypeaheadMultipleSelection__input"
          placeholder={`Search for ${this.props.label}`}
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
          </div>
          <div className="TypeaheadMultipleSelection__valueList">
            <div className="TypeaheadMultipleSelection__labeled-valueList">
              <p>Available {this.props.label}: </p>
              <li className="TypeaheadMultipleSelection__no-items">
                {loading ? '' : `No matching ${this.props.label} found`}
              </li>
            </div>
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

TypeaheadMultipleSelection.defaultProps = {
  format: v => v
};
