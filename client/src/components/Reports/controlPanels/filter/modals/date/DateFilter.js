/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import {
  Modal,
  Button,
  ButtonGroup,
  Input,
  Select,
  ErrorMessage,
  Form,
  DatePicker,
  Message,
  Labeled
} from 'components';
import './DateFilter.scss';
import {t} from 'translation';

export default class DateFilter extends React.Component {
  constructor(props) {
    super(props);

    let startDate, endDate, mode, dynamicValue, dynamicUnit;

    if (props.filterData) {
      if (props.filterData.data.type === 'fixed') {
        startDate = moment(props.filterData.data.start);
        endDate = moment(props.filterData.data.end);
      } else {
        mode = 'dynamic';
        dynamicValue = props.filterData.data.start.value;
        dynamicUnit = props.filterData.data.start.unit;
      }
    }

    this.state = {
      startDate: startDate || moment(),
      endDate: endDate || moment(),
      validDate: true,
      mode: mode || 'static',
      dynamicValue: dynamicValue || '7',
      dynamicUnit: dynamicUnit || 'days'
    };
  }

  createFilter = () => {
    if (this.state.mode === 'static') {
      this.props.addFilter({
        type: this.props.filterType,
        data: {
          type: 'fixed',
          start: this.state.startDate.startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
          end: this.state.endDate.endOf('day').format('YYYY-MM-DDTHH:mm:ss')
        }
      });
    } else if (this.state.mode === 'dynamic') {
      this.props.addFilter({
        type: this.props.filterType,
        data: {
          type: 'relative',
          start: {
            value: parseFloat(this.state.dynamicValue),
            unit: this.state.dynamicUnit
          },
          end: null
        }
      });
    }
  };

  setMode = mode => {
    const newState = {
      mode,
      validDate: true
    };

    if (mode === 'static') {
      newState.startDate = moment().subtract(this.state.dynamicValue, this.state.dynamicUnit);
      newState.endDate = moment();
    } else if (mode === 'dynamic') {
      const {startDate, endDate} = this.state;
      if (startDate.isValid() && endDate.isValid()) {
        newState.dynamicUnit = 'days';
        newState.dynamicValue = Math.round(
          moment.duration(endDate.endOf('day').diff(startDate.startOf('day'))).asDays()
        );
      }
    }

    this.setState(newState);
  };

  render() {
    const {mode, validDate, startDate, endDate, dynamicValue, dynamicUnit} = this.state;
    return (
      <Modal
        open={true}
        onClose={this.props.close}
        onConfirm={mode === 'dynamic' && validDate ? this.createFilter : undefined}
        className="DateFilter"
      >
        <Modal.Header>
          {t('common.filter.modalHeader', {
            type: t(`common.filter.types.${this.props.filterType}`)
          })}
        </Modal.Header>
        <Modal.Content>
          {this.props.filterType === 'endDate' && (
            <Message type="warning">{t('common.filter.dateModal.endDateWarning')}</Message>
          )}
          <ButtonGroup>
            <Button
              onClick={() => this.setMode('static')}
              name="button-static"
              active={mode === 'static'}
            >
              {t('common.filter.dateModal.fixedDate')}
            </Button>
            <Button
              onClick={() => this.setMode('dynamic')}
              name="button-dynamic"
              active={mode === 'dynamic'}
            >
              {t('common.filter.dateModal.relativeDate')}
            </Button>
          </ButtonGroup>
          {mode === 'static' && (
            <React.Fragment>
              <label className="tip">{t('common.filter.dateModal.selectDates')}</label>
              <DatePicker onDateChange={this.onDateChange} initialDates={{startDate, endDate}} />
            </React.Fragment>
          )}
          {mode === 'dynamic' && (
            <Form horizontal>
              <p className="tip">
                {t(`common.filter.dateModal.includeInstances.${this.props.filterType}`)}
              </p>
              <Form.Group noSpacing>
                <Labeled label={t('common.filter.dateModal.inLast')}>
                  <Form.InputGroup>
                    <Input
                      value={dynamicValue}
                      onChange={this.setDynamicValue}
                      isInvalid={!validDate}
                    />

                    <Select value={dynamicUnit} onChange={this.setDynamicUnit}>
                      <Select.Option value="minutes">
                        {t('common.unit.minute.label-plural')}
                      </Select.Option>
                      <Select.Option value="hours">
                        {t('common.unit.hour.label-plural')}
                      </Select.Option>
                      <Select.Option value="days">
                        {t('common.unit.day.label-plural')}
                      </Select.Option>
                      <Select.Option value="weeks">
                        {t('common.unit.week.label-plural')}
                      </Select.Option>
                      <Select.Option value="months">
                        {t('common.unit.month.label-plural')}
                      </Select.Option>
                      <Select.Option value="years">
                        {t('common.unit.year.label-plural')}
                      </Select.Option>
                    </Select>
                  </Form.InputGroup>
                </Labeled>
                {!validDate && (
                  <ErrorMessage>{t('common.filter.dateModal.invalidInput')}</ErrorMessage>
                )}
              </Form.Group>
            </Form>
          )}
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>{t('common.cancel')}</Button>
          <Button variant="primary" color="blue" disabled={!validDate} onClick={this.createFilter}>
            {this.props.filterData ? t('common.filter.editFilter') : t('common.filter.addFilter')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  setDynamicUnit = value => this.setState({dynamicUnit: value});
  setDynamicValue = ({target: {value}}) => {
    this.setState({
      dynamicValue: value,
      validDate: value.trim() && !isNaN(value.trim()) && +value > 0
    });
  };

  onDateChange = ({startDate, endDate, valid}) => {
    this.setState({startDate, endDate, validDate: valid});
  };
}
