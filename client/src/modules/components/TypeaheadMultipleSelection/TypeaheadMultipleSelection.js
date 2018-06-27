import React from 'react';
import {Input} from 'components';

import './TypeaheadMultipleSelection.css';

export default function TypeaheadMultipleSelection(props) {
  const mapSelectedValues = values => {
    return (
      values.length > 0 && (
        <div className="TypeaheadMultipleSelection__labeled-valueList">
          <p>Selected Values: </p>
          <div className="TypeaheadMultipleSelection__values-sublist">
            {values.map((value, idx) => {
              return (
                <li key={idx} className="TypeaheadMultipleSelection__valueListItem">
                  <label>
                    <Input type="checkbox" checked value={value} onChange={props.toggleValue} />
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

  const mapAvaliableValues = (availableValues, selectedValues) => {
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
                      onChange={props.toggleValue}
                    />
                    {value}
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

  const {availableValues, selectedValues, loading} = props;
  const input = (
    <div className="TypeaheadMultipleSelection__labeled-input">
      <p>Values that start with:</p>
      <Input className="TypeaheadMultipleSelection__input" onChange={props.setPrefix} />
    </div>
  );
  const loadingIndicator = loading ? (
    <div class="TypeaheadMultipleSelection__loading-indicator">Loading...</div>
  ) : (
    ''
  );
  if (availableValues.length === 0) {
    return (
      <div className="TypeaheadMultipleSelection">
        {input}
        {loadingIndicator}
        <div className="TypeaheadMultipleSelection__valueList">
          {mapSelectedValues(selectedValues)}
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
        {mapSelectedValues(selectedValues)}
        {mapAvaliableValues(availableValues, selectedValues)}
      </div>
    </div>
  );
}
