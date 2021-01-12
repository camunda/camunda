/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';
import {Modal, Button, Form, MessageBox, DateRangeInput} from 'components';

import DateFilterPreview from './DateFilterPreview';
import {convertFilterToState, convertStateToFilter, isValid} from './service';

import './DateFilter.scss';

export default class DateFilter extends React.Component {
  state = {
    valid: false,
    type: '',
    unit: '',
    customNum: '2',
    startDate: null,
    endDate: null,
  };

  componentDidMount() {
    const {filterData} = this.props;
    if (!filterData) {
      return;
    }

    this.setState(convertFilterToState(filterData.data));
  }

  confirm = () => {
    const {type, unit, customNum, startDate, endDate} = this.state;
    const {addFilter, filterType} = this.props;
    if (isValid(this.state)) {
      return addFilter({
        type: filterType,
        data: convertStateToFilter({type, unit, customNum, startDate, endDate}),
      });
    }
  };

  render() {
    const {type, unit, customNum, startDate, endDate} = this.state;
    const {close, filterData, filterType} = this.props;

    return (
      <Modal open={true} onClose={close} onConfirm={this.confirm} className="DateFilter">
        <Modal.Header>
          {t('common.filter.modalHeader', {
            type: t(`common.filter.types.${filterType}`),
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
            <DateRangeInput
              type={type}
              unit={unit}
              startDate={startDate}
              endDate={endDate}
              customNum={customNum}
              onChange={(change) => this.setState(change)}
            />
            <Form.Group className="previewContainer">
              {isValid(this.state) && (
                <DateFilterPreview
                  filterType={filterType}
                  filter={convertStateToFilter({
                    type,
                    unit,
                    customNum,
                    startDate,
                    endDate,
                  })}
                />
              )}
            </Form.Group>
          </Form>
        </Modal.Content>
        <Modal.Actions>
          <Button main onClick={close}>
            {t('common.cancel')}
          </Button>
          <Button main primary disabled={!isValid(this.state)} onClick={this.confirm}>
            {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
