import React from 'react';
import moment from 'moment';

import {Modal, Button} from 'components';

import './DateFilter.css';

import DateFields from './DateFields';
import DateButton from './DateButton';

export default class DateFilter extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      startDate: moment(),
      endDate: moment()
    };
  }

  createFilter = () => {
    this.props.addFilter({
      type: 'date',
      data: {
        start: this.state.startDate.startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
        end: this.state.endDate.endOf('day').format('YYYY-MM-DDTHH:mm:ss')
      }
    });
  }

  render() {
    return (<Modal open={true} onClose={this.props.close} className='DateFilter__modal'>
      <Modal.Header>Start Date Filter</Modal.Header>
      <Modal.Content>
        <form>
          <span>Select beginning and end dates for filter</span>
          <DateFields format='YYYY-MM-DD'
                      onDateChange={this.onDateChange}
                      startDate={this.state.startDate}
                      endDate={this.state.endDate} />
          <div>
            <span>Frequently Used:</span>
            <p className='DateFilter__buttonRow'>
              {this.getDateButtons([
                DateButton.TODAY,
                DateButton.YESTERDAY,
                DateButton.PAST7,
                DateButton.PAST30
              ])}
            </p>
            <p className='DateFilter__buttonRow'>
              {this.getDateButtons([
                DateButton.THIS_WEEK,
                DateButton.THIS_MONTH,
                DateButton.THIS_YEAR
              ])}
            </p>
            <p className='DateFilter__buttonRow'>
              {this.getDateButtons([
                DateButton.LAST_WEEK,
                DateButton.LAST_MONTH,
                DateButton.LAST_YEAR
              ])}
            </p>
          </div>
        </form>
      </Modal.Content>
      <Modal.Actions>
        <Button onClick={this.props.close}>Abort</Button>
        <Button type='primary' onClick={this.createFilter}>Create Filter</Button>
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
