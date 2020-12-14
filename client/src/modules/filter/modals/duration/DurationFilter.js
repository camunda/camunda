/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Modal, Button, Input, Select, Message, Form} from 'components';
import {numberParser} from 'services';

import './DurationFilter.scss';
import {t} from 'translation';

export default class DurationFilter extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      value: props.filterData ? props.filterData.data.value.toString() : '7',
      operator: props.filterData ? props.filterData.data.operator : '>',
      unit: props.filterData ? props.filterData.data.unit : 'days',
    };
  }

  createFilter = () => {
    this.props.addFilter({
      type: 'processInstanceDuration',
      data: {
        value: parseFloat(this.state.value),
        operator: this.state.operator,
        unit: this.state.unit,
      },
    });
  };

  render() {
    const {value, operator, unit} = this.state;
    const isValidInput = numberParser.isPositiveNumber(value);
    return (
      <Modal
        open={true}
        onClose={this.props.close}
        onConfirm={isValidInput ? this.createFilter : undefined}
        className="DurationFilter"
      >
        <Modal.Header>
          {t('common.filter.modalHeader', {
            type: t(`common.filter.types.instanceDuration`),
          })}
        </Modal.Header>
        <Modal.Content>
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
              {!isValidInput && <Message error>{t('common.errors.postiveNum')}</Message>}
            </Form.Group>
          </Form>
        </Modal.Content>
        <Modal.Actions>
          <Button main onClick={this.props.close}>
            {t('common.cancel')}
          </Button>
          <Button main primary disabled={!isValidInput} onClick={this.createFilter}>
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
