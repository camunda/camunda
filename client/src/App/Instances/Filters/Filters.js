import React from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import Button from 'modules/components/Button';
import TextInput from 'modules/components/TextInput';
import {DEFAULT_FILTER, FILTER_TYPES, DIRECTION} from 'modules/constants';
import {isEqual} from 'modules/utils';

import Filter from './Filter';
import * as Styled from './styled';

export default class Filters extends React.Component {
  static propTypes = {
    filter: PropTypes.object.isRequired,
    onFilterChange: PropTypes.func,
    onBulkFilterChange: PropTypes.func,
    resetFilter: PropTypes.func
  };

  handleErrorMessageChange = event => {
    const value = event.target.value;

    this.props.onFilterChange({
      errorMessage: value.length === 0 ? null : value
    });
  };

  render() {
    const {active, incidents, canceled, completed} = this.props.filter;

    return (
      <Panel isRounded>
        <Panel.Header isRounded>Filters</Panel.Header>
        <Panel.Body>
          <Styled.Filters>
            <Styled.BulkFilters>
              <TextInput
                name="errorMessage"
                placeholder="Error Message"
                onBlur={this.handleErrorMessageChange}
                aria-label="Error Message"
                aria-required="false"
              />
            </Styled.BulkFilters>
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
