/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Modal, Button, Input, Select, Message, Form} from 'components';
import {numberParser} from 'services';

import FilterDefinitionSelection from '../FilterDefinitionSelection';

import './DurationFilter.scss';
import {t} from 'translation';

export default class DurationFilter extends React.Component {
  constructor(props) {
    super(props);

    let applyTo = [
      {identifier: 'all', displayName: t('common.filter.definitionSelection.allProcesses')},
    ];

    if (props.filterData && props.filterData.appliedTo?.[0] !== 'all') {
      applyTo = props.filterData.appliedTo.map((id) =>
        props.definitions.find(({identifier}) => identifier === id)
      );
    }

    this.state = {
      value: props.filterData?.data.value.toString() ?? '7',
      operator: props.filterData?.data.operator ?? '>',
      unit: props.filterData?.data.unit ?? 'days',
      applyTo,
    };
  }

  createFilter = () => {
    const {value, operator, unit, applyTo} = this.state;

    this.props.addFilter({
      type: 'processInstanceDuration',
      data: {
        value: parseFloat(value),
        operator,
        unit,
      },
      appliedTo: applyTo.map(({identifier}) => identifier),
    });
  };

  render() {
    const {value, operator, unit, applyTo} = this.state;
    const {definitions} = this.props;

    const isValidInput = numberParser.isPositiveInt(value);
    const isValidFilter = isValidInput && applyTo.length > 0;

    return (
      <Modal
        open={true}
        onClose={this.props.close}
        onConfirm={isValidFilter ? this.createFilter : undefined}
        className="DurationFilter"
      >
        <Modal.Header>
          {t('common.filter.modalHeader', {
            type: t(`common.filter.types.instanceDuration`),
          })}
        </Modal.Header>
        <Modal.Content>
          <FilterDefinitionSelection
            availableDefinitions={definitions}
            applyTo={applyTo}
            setApplyTo={(applyTo) => this.setState({applyTo})}
          />
          <p className="description">{t('common.filter.durationModal.includeInstance')} </p>
          <Form horizontal>
            <Form.Group noSpacing>
              <div>
                <Select value={operator} onChange={this.setOperator}>
                  <Select.Option value=">">
                    {t('common.filter.durationModal.moreThan')}
                  </Select.Option>
                  <Select.Option value="<">
                    {t('common.filter.durationModal.lessThan')}
                  </Select.Option>
                </Select>
              </div>
              <Form.InputGroup>
                <Input
                  isInvalid={!isValidInput}
                  value={value}
                  onChange={this.setValue}
                  maxLength="8"
                />
                <Select value={unit} onChange={this.setUnit}>
                  <Select.Option value="millis">
                    {t('common.unit.milli.label-plural')}
                  </Select.Option>
                  <Select.Option value="seconds">
                    {t('common.unit.second.label-plural')}
                  </Select.Option>
                  <Select.Option value="minutes">
                    {t('common.unit.minute.label-plural')}
                  </Select.Option>
                  <Select.Option value="hours">{t('common.unit.hour.label-plural')}</Select.Option>
                  <Select.Option value="days">{t('common.unit.day.label-plural')}</Select.Option>
                  <Select.Option value="weeks">{t('common.unit.week.label-plural')}</Select.Option>
                  <Select.Option value="months">
                    {t('common.unit.month.label-plural')}
                  </Select.Option>
                  <Select.Option value="years">{t('common.unit.year.label-plural')}</Select.Option>
                </Select>
              </Form.InputGroup>
              {!isValidInput && <Message error>{t('common.errors.positiveInt')}</Message>}
            </Form.Group>
          </Form>
        </Modal.Content>
        <Modal.Actions>
          <Button main onClick={this.props.close}>
            {t('common.cancel')}
          </Button>
          <Button main primary disabled={!isValidFilter} onClick={this.createFilter}>
            {this.props.filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  setOperator = (operator) => this.setState({operator});
  setUnit = (unit) => this.setState({unit});
  setValue = ({target: {value}}) => this.setState({value});
}
