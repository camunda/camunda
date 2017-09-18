import React from 'react';
import Modal from 'react-bootstrap/lib/Modal';
import DatePicker from 'react-bootstrap-date-picker';
import {onNextTick, withState} from 'utils';
import {createStartDateFilter, formatDate} from './service';
import {DateButton, TODAY, YESTERDAY, PAST7, PAST30,
        LAST_WEEK, LAST_MONTH, LAST_YEAR,
        THIS_WEEK, THIS_MONTH, THIS_YEAR} from './DateButton';
import {createViewUtilsComponentFromReact} from 'reactAdapter';

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
          startDate: new Date().toISOString(),
          endDate: new Date().toISOString()
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
                  <DatePicker dateFormat="YYYY-MM-DD"
                              onChange={this.getDateSetter('startDate')}
                              value={this.state.startDate} />
                  <span className="input-group-addon">to</span>
                  <DatePicker dateFormat="YYYY-MM-DD"
                              onChange={this.getDateSetter('endDate')}
                              value={this.state.endDate} />
                </div>
              </center>
                <div className="form-group">
                  <span className="label">Frequently Used:</span>
                  <p className="four-button-row">
                    {this.getDateButtons([TODAY, YESTERDAY, PAST7, PAST30])}
                  </p>
                  <p className="three-button-row">
                    {this.getDateButtons([LAST_WEEK, LAST_MONTH, LAST_YEAR])}
                  </p>
                  <p className="three-button-row">
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

      getDateSetter(name) {
        return date => {
          this.setState({
            ...this.state,
            [name]: date
          });
        };
      }

      createFilter = () => {
        createStartDateFilter(
          formatDate(new Date(this.state.startDate)) + 'T00:00:00',
          formatDate(new Date(this.state.endDate)) + 'T00:00:00'
        );

        this.close();

        onNextTick(createCallback);
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
