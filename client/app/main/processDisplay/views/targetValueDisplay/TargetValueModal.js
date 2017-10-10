import React from 'react';
const jsx = React.createElement;

import Modal from 'react-bootstrap/lib/Modal';

import {onNextTick} from 'utils';
import {getDefinitionId} from 'main/processDisplay/service';
import {setTargetValue, saveTargetValues} from './service';
import {TargetValueInput} from './TargetValueInput';
import * as timeUtil from 'utils/formatTime';

const timeUnits = ['w', 'd', 'h', 'm', 's', 'ms'];

function createDefaultTimeObject() {
  return timeUnits.reduce((obj, unit) => {
    obj[unit] = 0;
    return obj;
  }, {});
}

export class TargetValueModal extends React.Component {
  constructor(props) {
    super(props);

    this.state = createDefaultTimeObject();

    this.setValue = timeUnits.reduce((obj, unit) => {
      obj[unit] = this.setValueFor(unit);
      return obj;
    }, {});
  }

  setValueFor = unit => evt => {
    this.setState({
      [unit]: parseInt(evt.target.value, 10)
    });
  }

  componentWillReceiveProps(nextProps) {
    if (nextProps.isOpen && !this.props.isOpen) {
      // set the current state to the target value of the element
      const {element, targetValues} = nextProps;

      this.setState(timeUtil
        .formatTime(targetValues[element.businessObject.id], {returnRaw: true})
        .reduce((obj, {name, howMuch}) => {
          obj[name] = howMuch;
          return obj;
        }, createDefaultTimeObject())
      );
    }
  }

  render() {
    const {isOpen, element, close} = this.props;
    const elementName = element && element.businessObject.name;

    return (
      <Modal show={isOpen} onHide={close}>
        <Modal.Header>
          <h4 className="modal-title">Set Target Value for "{elementName}"</h4>
        </Modal.Header>
        <Modal.Body>
          <div className="form-group">
            <label>Duration</label>
            <table style={{textAlign: 'center'}}>
              <tbody>
                <tr>
                  {timeUnits.map(unit => <TargetValueInput key={unit} val={this.state[unit]} onChange={this.setValue[unit]} />)}
                  <td>
                    <button type="button" className="btn btn-default" onClick={this.resetForm}>
                      <span className="glyphicon glyphicon-remove" aria-hidden="true" />
                    </button>
                  </td>
                </tr>
                <tr>
                  <td>Weeks</td>
                  <td>Days</td>
                  <td>Hours</td>
                  <td>Minutes</td>
                  <td>Seconds</td>
                  <td>Milliseconds</td>
                  <td />
                </tr>
              </tbody>
            </table>
          </div>
        </Modal.Body>
        <Modal.Footer>
            <button type="button" className="btn btn-default" onClick={close}>
              Abort
            </button>
            <button type="button" className="btn btn-primary" onClick={this.storeTargetValue}>
              Set Target Value
            </button>
          </Modal.Footer>
      </Modal>
    );
  }

  resetForm = () => {
    this.setState(createDefaultTimeObject());
  }

  storeTargetValue = () => {
    const targetDurationInMillis = timeUnits
      .map(unit => this.state[unit] * timeUtil[unit])
      .reduce((prev, curr) => prev + curr, 0);

    setTargetValue(this.props.element, targetDurationInMillis);
    this.props.close();

    // wait for redux update to finish, then send up to date data to backend
    onNextTick(() => {
      saveTargetValues(getDefinitionId(), this.props.targetValues);
    });
  }
}
