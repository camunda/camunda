/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import {Modal, Button, Input, Select, Message, Form, DatePicker, MessageBox} from 'components';
import './DateFilter.scss';
import {t} from 'translation';
import {numberParser} from 'services';
import DateFilterPreview from './DateFilterPreview';
import {convertFilterToState, convertStateToFilter} from './service';

export default class DateFilter extends React.Component {
  state = {
    pickerValid: true,
    dateType: '',
    unit: '',
    customUnit: 'days',
    customNum: '2',
    startDate: moment(),
    endDate: moment()
  };

  componentDidMount() {
    const {filterData} = this.props;
    if (!filterData) {
      return;
    }

    this.setState(convertFilterToState(filterData.data));
  }

  confirm = () => {
    const {dateType, unit, customUnit, customNum, startDate, endDate} = this.state;
    const {addFilter, filterType} = this.props;

    return addFilter({
      type: filterType,
      data: convertStateToFilter({dateType, unit, customUnit, customNum, startDate, endDate})
    });
  };

  onDateChange = ({startDate, endDate, valid}) =>
    this.setState({startDate, endDate, pickerValid: valid});

  isValid = () => {
    const {dateType, unit, customNum, pickerValid} = this.state;
    switch (dateType) {
      case 'today':
      case 'yesterday':
        return true;
      case 'fixed':
        return pickerValid;
      case 'this':
      case 'last':
        const customNumberValid = numberParser.isPostiveInt(customNum);
        return unit && (unit !== 'custom' || customNumberValid);
      default:
        return false;
    }
  };

  render() {
    const {dateType, unit, customUnit, customNum, startDate, endDate} = this.state;
    const {close, filterData, filterType} = this.props;

    return (
      <Modal open={true} onClose={close} onConfirm={this.confirm} className="DateFilter">
        <Modal.Header>
          {t('common.filter.modalHeader', {
            type: t(`common.filter.types.${filterType}`)
          })}
        </Modal.Header>
        <Modal.Content>
          {filterType === 'endDate' && (
            <MessageBox type="warning">{t('common.filter.dateModal.endDateWarning')}</MessageBox>
          )}
          <Form>
            <span
              className="tip"
              dangerouslySetInnerHTML={{__html: t(`common.filter.dateModal.info.${filterType}`)}}
            />
            <Form.Group>
              <Form.InputGroup className="selectGroup">
                <Select onChange={dateType => this.setState({dateType, unit: ''})} value={dateType}>
                  <Select.Option value="today">
                    {t('common.filter.dateModal.unit.today')}
                  </Select.Option>
                  <Select.Option value="yesterday">
                    {t('common.filter.dateModal.unit.yesterday')}
                  </Select.Option>
                  <Select.Option value="this">
                    {t('common.filter.dateModal.unit.this')}
                  </Select.Option>
                  <Select.Option value="last">
                    {t('common.filter.dateModal.unit.last')}
                  </Select.Option>
                  <Select.Option value="fixed">
                    {t('common.filter.dateModal.unit.fixed')}
                  </Select.Option>
                </Select>
                <div className="unitSelection">
                  {dateType === 'fixed' ? (
                    <DatePicker onDateChange={this.onDateChange} />
                  ) : (
                    <>
                      <Select
                        disabled={dateType !== 'this' && dateType !== 'last'}
                        onChange={unit => this.setState({unit})}
                        value={unit}
                      >
                        <Select.Option value="weeks">{t('common.unit.week.label')}</Select.Option>
                        <Select.Option value="months">{t('common.unit.month.label')}</Select.Option>
                        <Select.Option value="years">{t('common.unit.year.label')}</Select.Option>
                        <Select.Option value="quarters">
                          {t('common.unit.quarter.label')}
                        </Select.Option>
                        {dateType === 'last' && (
                          <Select.Option className="customDate" value="custom">
                            {t('common.filter.dateModal.unit.custom')}
                          </Select.Option>
                        )}
                      </Select>
                      {unit === 'custom' && (
                        <>
                          <Input
                            className="number"
                            value={customNum}
                            onChange={({target: {value}}) => this.setState({customNum: value})}
                          />
                          <Select
                            onChange={customUnit => this.setState({customUnit})}
                            value={customUnit}
                          >
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
                          {!numberParser.isPostiveInt(customNum) && (
                            <Message error>{t('common.filter.dateModal.invalidInput')}</Message>
                          )}
                        </>
                      )}
                    </>
                  )}
                </div>
              </Form.InputGroup>
            </Form.Group>
            <Form.Group>
              {this.isValid() && (
                <DateFilterPreview
                  filterType={filterType}
                  filter={convertStateToFilter({
                    dateType,
                    unit,
                    customUnit,
                    customNum,
                    startDate,
                    endDate
                  })}
                />
              )}
            </Form.Group>
          </Form>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={close}>{t('common.cancel')}</Button>
          <Button variant="primary" color="blue" disabled={!this.isValid()} onClick={this.confirm}>
            {filterData ? t('common.filter.editFilter') : t('common.filter.addFilter')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
