import React from 'react';
import moment from 'moment';

import {Modal, Button, ButtonGroup} from 'components';

import './DateFilter.css';

import DateFields from './DateFields';
import DateButton from './DateButton';

export default class DateFilter extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      startDate: moment(),
      endDate: moment(),
      validDate: true
    };
  }

  createFilter = () => {
    this.props.addFilter({
      type: 'date',
      data: {
        type: 'start_date',
        operator: '>=',
        value: this.state.startDate.startOf('day').format('YYYY-MM-DDTHH:mm:ss')
      }
    }, {
      type: 'date',
      data: {
        type: 'start_date',
        operator: '<=',
        value: this.state.endDate.endOf('day').format('YYYY-MM-DDTHH:mm:ss')
      }
    });
  }

  render() {
    return (<Modal open={true} onClose={this.props.close} className='DateFilter__modal'>
      <Modal.Header>Start Date Filter</Modal.Header>
      <Modal.Content>
        <div className='DateFilter__inputs'>
          <label className='DateFilter__input-label'>Select start and end dates to filter by:</label>
          <DateFields format='YYYY-MM-DD'
                    onDateChange={this.onDateChange}
                    startDate={this.state.startDate}
                    endDate={this.state.endDate}
                    disableAddButton = {this.disableAddButton} />
        </div>
        <div className='DateFilter__buttons'>
          <ButtonGroup className='DateFilter__buttonRow'>
            {this.getDateButtons([
              DateButton.TODAY,
              DateButton.YESTERDAY,
              DateButton.PAST7,
              DateButton.PAST30
            ])}
          </ButtonGroup>
          <ButtonGroup className='DateFilter__buttonRow'>
            {this.getDateButtons([
              DateButton.THIS_WEEK,
              DateButton.THIS_MONTH,
              DateButton.THIS_YEAR
            ])}
          </ButtonGroup>
          <ButtonGroup className='DateFilter__buttonRow'>
            {this.getDateButtons([
              DateButton.LAST_WEEK,
              DateButton.LAST_MONTH,
              DateButton.LAST_YEAR
            ])}
          </ButtonGroup>
        </div>
      </Modal.Content>
      <Modal.Actions>
        <Button onClick={this.props.close}>Cancel</Button>
        <Button type='primary' color='blue' disabled={!this.state.validDate} onClick={this.createFilter}>Add Filter</Button>
      </Modal.Actions>
    </Modal>
    );
  }

  getDateButtons(labels) {
    return labels.map(label =>
      <DateButton dateLabel={label}
                  key={label}
                  setDates={this.setDates} />
    );
  }

  setDates = dates => {
    this.setState(dates);
  }

  disableAddButton = (isValid) => {
    this.setState({
      validDate: isValid
    })
  }

  onDateChange = (name, date) => {
    if ((name === 'startDate' && date.isAfter(this.state.endDate)) ||
        (name === 'endDate' && date.isBefore(this.state.startDate))) {
      return this.setState({
        startDate: date,
        endDate: date.clone()
      });
    }

    this.setState({
      [name]: date
    });
  }
}
