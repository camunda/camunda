import React from 'react';
import Modal from 'react-bootstrap/lib/Modal';
import {onNextTick, withState} from 'utils';
import moment from 'moment';
import {createViewUtilsComponentFromReact} from 'reactAdapter';
import {createStartDateFilter, sortDates, formatDate, FORMAT} from './service';
import {DateButton, TODAY, YESTERDAY, PAST7, PAST30,
        LAST_WEEK, LAST_MONTH, LAST_YEAR,
        THIS_WEEK, THIS_MONTH, THIS_YEAR} from './DateButton';
import {DateRange} from './DateRange';
import {DateInput} from './DateInput';

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
        return <Modal show={this.props.isOpen}>
          <Modal.Header>
            <button type="button" className="close" onClick={this.close}>
              <span>×</span>
            </button>
            <h4 className="modal-title">New Date Filter</h4>
          </Modal.Header>
          <Modal.Body>
            <form>
              <span className="label">Start Date Filter:</span>
              <center>
                <div className="input-group input-daterange">
                  <DateInput className="form-control start"
                             format={FORMAT}
                             onDateChange={this.getDateSetter('startDate')}
                             date={this.state.startDate} />
                  <span className="input-group-addon">to</span>
                  <DateInput className="form-control end"
                             format={FORMAT}
                             onDateChange={this.getDateSetter('endDate')}
                             date={this.state.endDate} />
                </div>
                <DateRange
                    onDateChange={this.onDateChange}
                    startDate={this.state.startDate}
                    endDate={this.state.endDate} />
              </center>
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

      sortDates({startDate, endDate}) {
        const {start, end} = sortDates({
          start: startDate,
          end: endDate
        });

        return {
          startDate: start,
          endDate: end
        };
      }

      getDateButtons(labels) {
        return labels.map(label =>
          <DateButton dateLabel={label}
                      key={label}
                      setDates={this.setDates} />
        );
      }

      setDates = (dates) => {
        this.setState(
          this.sortDates(dates)
        );
      }

      onDateChange = (name, date) => {
        let correctName = name;

        if (name === 'startDate' && date.isAfter(this.state.endDate)) {
          correctName = 'endDate';
        }

        if (name === 'endDate' && date.isBefore(this.state.startDate)) {
          correctName = 'startDate';
        }

        const range = this.sortDates({
          ...this.state,
          [correctName]: date
        });

        this.setState(range);
      }

      getDateSetter(name) {
        return this.onDateChange.bind(this, name);
      }

      createFilter = () => {
        createStartDateFilter(
          formatDate(this.state.startDate) + 'T00:00:00',
          formatDate(this.state.endDate) + 'T23:59:59'
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
