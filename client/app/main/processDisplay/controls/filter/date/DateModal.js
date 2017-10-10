import React from 'react';
import Modal from 'react-bootstrap/lib/Modal';
import {onNextTick, withState} from 'utils';
import moment from 'moment';
import {createViewUtilsComponentFromReact} from 'reactAdapter';
import {createStartDateFilter, formatDate, FORMAT} from './service';
import {DateButton, TODAY, YESTERDAY, PAST7, PAST30,
        LAST_WEEK, LAST_MONTH, LAST_YEAR,
        THIS_WEEK, THIS_MONTH, THIS_YEAR} from './DateButton';
import {DateFields} from './DateFields';

const jsx = React.createElement;

export function createDateModalReact(createCallback) {
  return withState(
    {
      isOpen: false
    },
    class extends React.PureComponent {
      constructor(props) {
        super(props);

        this.state = {
          startDate: moment(),
          endDate: moment()
        };
      }

      render() {
        return <Modal show={this.props.isOpen} onHide={this.close}>
          <Modal.Header>
            <button type="button" className="close" onClick={this.close}>
              <span>×</span>
            </button>
            <h4 className="modal-title">Start Date Filter</h4>
          </Modal.Header>
          <Modal.Body>
            <form>
              <span>Select beginning and end dates for filter</span>
              <DateFields format={FORMAT}
                          onDateChange={this.onDateChange}
                          startDate={this.state.startDate}
                          endDate={this.state.endDate} />
              <div className="form-group">
                <span className="label">Frequently Used:</span>
                <p className="button-row">
                  {this.getDateButtons([TODAY, YESTERDAY, PAST7, PAST30])}
                </p>
                <p className="button-row">
                  {this.getDateButtons([LAST_WEEK, LAST_MONTH, LAST_YEAR])}
                </p>
                <p className="button-row">
                  {this.getDateButtons([THIS_WEEK, THIS_MONTH, THIS_YEAR])}
                </p>
              </div>
            </form>
          </Modal.Body>
          <Modal.Footer>
            <button type="button" className="btn btn-default" onClick={this.close}>
              Abort
            </button>
            <button type="button" className="btn btn-primary" onClick={this.createFilter}>
              Create Filter
            </button>
          </Modal.Footer>
        </Modal>;
      }
      getDateButtons(labels) {
        return labels.map(label =>
          <DateButton dateLabel={label}
                      key={label}
                      setDates={this.setDates} />
        );
      }

      setDates = (dates) => {
        this.setState(dates);
      }

      onDateChange = (name, date) => {
        if (name === 'startDate' && date.isAfter(this.state.endDate) ||
            name === 'endDate' && date.isBefore(this.state.startDate)) {
          return this.setState({
            startDate: date,
            endDate: date.clone()
          });
        }

        this.setState({
          [name]: date
        });
      }

      createFilter = () => {
        createStartDateFilter(
          formatDate(this.state.startDate, {
            withTime: true
          }),
          formatDate(this.state.endDate, {
            withTime: true,
            endOfDay: true
          })
        );

        this.close();

        onNextTick(createCallback);
      }

      componentWillReceiveProps({isOpen}) {
        if (!isOpen) {
          this.setState({
            startDate: moment(),
            endDate: moment()
          });
        }
      }

      switchModal(isOpen) {
        if (typeof this.props.setProperty === 'function') {
          this.props.setProperty('isOpen', isOpen);
        }
      }

      open = () => this.switchModal(true)
      close = () => this.switchModal(false)
    }
  );
}

export function createDateModal(createCallback) {
  const DateModalReact = createDateModalReact(createCallback);
  const DateModal = createViewUtilsComponentFromReact('div', DateModalReact);

  DateModal.open = () => DateModalReact.setProperty('isOpen', true);
  DateModal.close = () => DateModalReact.setProperty('isOpen', false);

  return DateModal;
}
