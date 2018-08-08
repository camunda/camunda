import React from 'react';
import debounce from 'debounce';

import {ButtonGroup, Button, TypeaheadMultipleSelection, LoadingIndicator} from 'components';
import classnames from 'classnames';

import {loadValues} from './service';

import './StringInput.css';

const valuesToLoad = 10;

export default class StringInput extends React.Component {
  static defaultFilter = {operator: 'in', values: []};

  state = {
    loading: false,
    prefix: '',
    availableValues: [],
    valuesLoaded: 0,
    valuesAreComplete: false,
    numberOfUnselectedValuesToDisplay: valuesToLoad
  };

  reset() {
    this.props.setValid(false);
    this.loadAvailableValues();
  }

  componentDidMount() {
    this.reset();
  }

  componentDidUpdate(prevProps) {
    if (prevProps.variable !== this.props.variable) {
      this.reset();
    }
  }

  loadAvailableValues = debounce(more => {
    this.setState(
      {
        loading: true
      },
      async () => {
        const values = await loadValues(
          this.props.processDefinitionKey,
          this.props.processDefinitionVersion,
          this.props.variable.name,
          this.props.variable.type,
          0,
          this.state.valuesLoaded + valuesToLoad + this.props.filter.values.length + 1,
          this.state.prefix
        );

        const numberOfUnselectedValuesToDisplay =
          this.state.numberOfUnselectedValuesToDisplay + (more ? valuesToLoad : 0);

        const availableValues = values.slice(
          0,
          numberOfUnselectedValuesToDisplay + this.selectedAvailableValues(values).length
        );

        const valuesAreComplete =
          values.length <
          this.state.valuesLoaded + valuesToLoad + this.availableSelectedValues(values).length + 1;

        this.setState({
          availableValues,
          valuesLoaded: availableValues.length,
          numberOfUnselectedValuesToDisplay,
          valuesAreComplete,
          loading: false
        });
      }
    );
  }, 300);

  selectedAvailableValues = availableValues => {
    return availableValues.filter(value => this.props.filter.values.includes(value));
  };

  availableSelectedValues = availableValues => {
    return this.props.filter.values.filter(value => availableValues.includes(value));
  };

  setOperator = operator => evt => {
    evt.preventDefault();
    this.props.changeFilter({operator, values: this.props.filter.values});
  };

  loadMore = evt => {
    evt.preventDefault();
    this.loadAvailableValues(true);
  };

  setValuePrefix = async evt => {
    const queryIncluded = this.state.prefix.slice(0, -1) === evt.target.value;
    this.setState(
      {
        prefix: evt.target.value,
        valuesLoaded: queryIncluded ? this.props.filter.values.length : 0,
        numberOfUnselectedValuesToDisplay: valuesToLoad
      },
      this.loadAvailableValues
    );
  };

  toggleValue = ({target: {checked, value}}) => {
    let newValues;
    if (checked) {
      newValues = this.props.filter.values.concat(value);
    } else {
      newValues = this.props.filter.values.filter(existingValue => existingValue !== value);
    }
    this.props.changeFilter({
      operator: this.props.filter.operator,
      values: newValues
    });
    this.props.setValid(newValues.length > 0);
  };

  render() {
    const {operator} = this.props.filter;

    return (
      <React.Fragment>
        <div className="VariableFilter__buttonRow">
          <ButtonGroup>
            <Button
              onClick={this.setOperator('in')}
              className={classnames({'is-active': operator === 'in'})}
            >
              is
            </Button>
            <Button
              onClick={this.setOperator('not in')}
              className={classnames({'is-active': operator === 'not in'})}
            >
              is not
            </Button>
          </ButtonGroup>
        </div>
        <div className="VariableFilter__valueFields">
          <div className="StringInput__selection">
            <TypeaheadMultipleSelection
              availableValues={this.state.availableValues}
              selectedValues={this.props.filter.values}
              setPrefix={this.setValuePrefix}
              toggleValue={this.toggleValue}
              loading={this.state.loading ? 1 : 0}
            />
            {!this.state.valuesAreComplete && (
              <Button
                className="StringInput__load-more-button"
                onClick={this.loadMore}
                disabled={this.state.loading}
              >
                {this.state.loading ? <LoadingIndicator small /> : 'Load More'}
              </Button>
            )}
          </div>
        </div>
      </React.Fragment>
    );
  }
}
