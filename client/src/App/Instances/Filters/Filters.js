import React from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import Button from 'modules/components/Button';
import TextInput from 'modules/components/TextInput';
import Textarea from 'modules/components/Textarea';
import {DEFAULT_FILTER, FILTER_TYPES, DIRECTION} from 'modules/constants';
import {isEqual} from 'modules/utils';

import Filter from './Filter';
import * as Styled from './styled';

const PLACEHOLDER = {
  errorMessage: 'Error Message',
  instanceIds: 'Instance Id(s) separated by space or comma'
};

const fieldParser = {
  errorMessage: value => (value.length === 0 ? null : value),
  ids: value => value.split(/[ ,]+/).filter(Boolean)
};

export default class Filters extends React.Component {
  static propTypes = {
    filter: PropTypes.object.isRequired,
    onFilterChange: PropTypes.func,
    onBulkFilterChange: PropTypes.func,
    resetFilter: PropTypes.func
  };

  handleFieldChange = event => {
    const {value, name} = event.target;

    this.props.onFilterChange({
      [name]: fieldParser[name](value)
    });
  };

  render() {
    const {active, incidents, canceled, completed} = this.props.filter;

    return (
      <Panel isRounded>
        <Panel.Header isRounded>Filters</Panel.Header>
        <Panel.Body>
          <Styled.Filters>
            <Styled.Field>
              <TextInput
                name="errorMessage"
                placeholder={PLACEHOLDER.errorMessage}
                onBlur={this.handleFieldChange}
                aria-label={PLACEHOLDER.errorMessage}
                aria-required="false"
              />
            </Styled.Field>
            <Styled.Field>
              <Textarea
                name="ids"
                placeholder={PLACEHOLDER.instanceIds}
                onBlur={this.handleFieldChange}
                aria-label={PLACEHOLDER.instanceIds}
                aria-required="false"
              />
            </Styled.Field>
            <Filter
              type={FILTER_TYPES.RUNNING}
              filter={{
                active,
                incidents
              }}
              onChange={this.props.onFilterChange}
            />
            <Filter
              type={FILTER_TYPES.FINISHED}
              filter={{
                completed,
                canceled
              }}
              onChange={this.props.onFilterChange}
            />
          </Styled.Filters>
        </Panel.Body>
        <Styled.ExpandButton direction={DIRECTION.LEFT} isExpanded={true} />
        <Styled.ResetButtonContainer>
          <Button
            title="Reset filters"
            disabled={isEqual(this.props.filter, DEFAULT_FILTER)}
            onClick={this.props.resetFilter}
          >
            Reset Filters
          </Button>
        </Styled.ResetButtonContainer>
        <Panel.Footer />
      </Panel>
    );
  }
}
