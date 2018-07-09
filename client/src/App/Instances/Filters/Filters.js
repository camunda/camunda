import React from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import Button from 'modules/components/Button';
import {DEFAULT_FILTER, FILTER_TYPES} from 'modules/constants/filter';
import {ICON_DIRECTION} from 'modules/constants/expandIcon';
import {isEqual} from 'modules/utils';

import Filter from './Filter';
import * as Styled from './styled';

export default class Filters extends React.Component {
  static propTypes = {
    filter: PropTypes.object.isRequired,
    handleFilterChange: PropTypes.func,
    resetFilter: PropTypes.func
  };

  render() {
    const {active, incidents, canceled, completed} = this.props.filter;
    return (
      <Styled.Filters>
        <Panel isRounded>
          <Panel.Header isRounded>Filters</Panel.Header>
          <Panel.Body>
            <Filter
              type={FILTER_TYPES.RUNNING}
              filter={{
                active,
                incidents
              }}
              onChange={this.props.handleFilterChange}
            />
            <Filter
              type={FILTER_TYPES.FINISHED}
              filter={{
                completed,
                canceled
              }}
              onChange={this.props.handleFilterChange}
            />
          </Panel.Body>
          <Styled.ExpandButton
            iconDirection={ICON_DIRECTION.LEFT}
            isExpanded={true}
          />
          <Styled.ResetButtonContainer>
            <Button
              title="reset filters"
              disabled={isEqual(this.props.filter, DEFAULT_FILTER)}
              onClick={this.props.resetFilter}
            >
              Reset Filters
            </Button>
          </Styled.ResetButtonContainer>
          <Panel.Footer />
        </Panel>
      </Styled.Filters>
    );
  }
}
