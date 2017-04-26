import {jsx, createReferenceComponent, Socket, OnEvent} from 'view-utils';
import {createModal} from 'widgets';
import {onNextTick} from 'utils';
import {setTargetValue, saveTargetValues, getTargetValue, getTargetDurationFromForm, setTargetDurationToForm} from './service';
import {TargetValueInput} from './TargetValueInput';

export function createTargetValueModal(State, getProcessDefinition) {
  const Modal = createModal();
  const Reference = createReferenceComponent();

  let element;

  const TargetValueModal = () => {
    return (parentNode, eventsBus) => {
      const template = <Modal>
        <Socket name="head">
          <h4 className="modal-title">Set Target Value for {'"'}
            <span>
              <Reference name="elementName" />
            </span>
            {'"'}
          </h4>
        </Socket>
        <Socket name="body">
          <div className="form-group">
            <Reference name="durationForm" />
            <label>Duration</label>
            <table style="text-align: center;">
              <tr>
                <TargetValueInput unit="w" />
                <TargetValueInput unit="d" />
                <TargetValueInput unit="h" />
                <TargetValueInput unit="m" />
                <TargetValueInput unit="s" />
                <TargetValueInput unit="ms" />
              </tr>
              <tr>
                <td>Weeks</td>
                <td>Days</td>
                <td>Hours</td>
                <td>Minutes</td>
                <td>Seconds</td>
                <td>Milliseconds</td>
              </tr>
            </table>
          </div>
        </Socket>
        <Socket name="foot">
          <button type="button" className="btn btn-default">
            <OnEvent event="click" listener={Modal.close} />
            Abort
          </button>
          <button type="button" className="btn btn-primary">
            <OnEvent event="click" listener={storeTargetValue} />
            Set Target Value
          </button>
        </Socket>
      </Modal>;

      return template(parentNode, eventsBus);

      function storeTargetValue() {
        const form = Reference.getNode('durationForm');
        const targetDuration = getTargetDurationFromForm(form);

        setTargetValue(element, targetDuration);
        Modal.close();

        // wait for redux update to finish, then send up to date data to backend
        onNextTick(() => {
          saveTargetValues(getProcessDefinition(), State.getState().targetValue.data);
        });
      }
    };
  };

  TargetValueModal.open = function(targetValueElement) {
    element = targetValueElement;
    Reference.getNode('elementName').textContent = element.businessObject.name;

    setTargetDurationToForm(Reference.getNode('durationForm'), getTargetValue(State, element));

    Modal.open();
  };

  return TargetValueModal;
}
