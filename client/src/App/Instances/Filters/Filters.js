import React from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import Button from 'modules/components/Button';
import TextInput from 'modules/components/TextInput';
import Textarea from 'modules/components/Textarea';
import Select from 'modules/components/Select';
import {DEFAULT_FILTER, FILTER_TYPES, DIRECTION} from 'modules/constants';
import {isEqual} from 'modules/utils';
import * as api from 'modules/api/instances';

import CheckboxGroup from './CheckboxGroup';
import * as Styled from './styled';
import {parseWorkflowNames, fieldParser} from './service';

const FIELDS = {
  errorMessage: {name: 'errorMessage', placeholder: 'Error Message'},
  ids: {name: 'ids', placeholder: 'Instance Id(s) separated by space or comma'},
  workflowName: {name: 'workflowName', placeholder: 'Workflow'}
};

export default class Filters extends React.Component {
  static propTypes = {
    filter: PropTypes.object.isRequired,
    onFilterChange: PropTypes.func,
    onBulkFilterChange: PropTypes.func,
    resetFilter: PropTypes.func
  };

  state = {
    workflowNames: [],
    currentWorkflowName: ''
  };

  componentDidMount = async () => {
    this.setState({
      workflowNames: await api.fetchGroupedWorkflowInstances()
    });
  };

  handleWorkflowsNameChange = event => {
    const {value} = event.target;

    this.setState({
      currentWorkflowName: value
    });
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
              <Select
                value={this.state.currentWorkflowName}
                disabled={this.state.workflowNames.length === 0}
                name={FIELDS.workflowName.name}
                placeholder={FIELDS.workflowName.placeholder}
                options={parseWorkflowNames(this.state.workflowNames)}
                onChange={this.handleWorkflowsNameChange}
              />
            </Styled.Field>
            <Styled.Field>
              <Textarea
                name={FIELDS.ids.name}
                placeholder={FIELDS.ids.placeholder}
                onBlur={this.handleFieldChange}
              />
            </Styled.Field>
            <Styled.Field>
              <TextInput
                name={FIELDS.errorMessage.name}
                placeholder={FIELDS.errorMessage.placeholder}
                onBlur={this.handleFieldChange}
              />
            </Styled.Field>
            <CheckboxGroup
              type={FILTER_TYPES.RUNNING}
              filter={{
                active,
                incidents
              }}
              onChange={this.props.onFilterChange}
            />
            <CheckboxGroup
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
