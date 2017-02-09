import {jsx, OnEvent, Socket, createReferenceComponent, $window} from 'view-utils';
import {openModal, closeModal, createStartDateFilter} from './service';
import {Dropdown, DropdownItem, Modal} from 'widgets';

export function CreateFilter({onFilterAdded}) {
  const nodes = {};
  const Reference = createReferenceComponent(nodes);

  return <td>
    <Dropdown>
      <Socket name="label">
        + <span className="caret"></span>
      </Socket>
      <Socket name="list">
        <DropdownItem listener={openModal}>
          Start Date
        </DropdownItem>
      </Socket>
    </Dropdown>
    <Modal isOpen={isModalOpen} onClose={closeModal}>
      <Socket name="head">
        <button type="button" className="close">
          <OnEvent event='click' listener={closeModal} />
          <span>×</span>
        </button>
        <h4 className="modal-title">New Filter</h4>
      </Socket>
      <Socket name="body">
        <form>
          <span className="label">Start Date Filter:</span>
            <center>
              <div className="input-group input-daterange">
                <input type="text" className="form-control start" value="2017-01-01">
                  <Reference name="startDate" />
                </input>
                <span className="input-group-addon">to</span>
                <input type="text" className="form-control end" value="2017-12-31">
                  <Reference name="endDate" />
                </input>
              </div>
            </center>
          <div className="form-group">
            <span className="label">Frequently Used:</span>
            <p>
              <button type="button" className="btn btn-default active" style="width:21%;">Today</button>
              <button type="button" className="btn btn-default" style="width:21%;">Yesterday</button>
              <button type="button" className="btn btn-default" style="width:21%;">Past 7 days</button>
              <button type="button" className="btn btn-default" style="width:21%;">Past 30 days</button>
            </p>
            <p>
              <button type="button" className="btn btn-default" style="width:28%;">Last Week</button>
              <button type="button" className="btn btn-default" style="width:28%;">Last Month</button>
              <button type="button" className="btn btn-default" style="width:28%;">Last Year</button>
            </p>
            <p>
              <button type="button" className="btn btn-default" style="width:28%;">This Week</button>
              <button type="button" className="btn btn-default" style="width:28%;">This Month</button>
              <button type="button" className="btn btn-default" style="width:28%;">This Year</button>
            </p>
          </div>
        </form>
      </Socket>
      <Socket name="foot">
        <button type="button" className="btn btn-default">
          <OnEvent event='click' listener={closeModal} />
          Abort
        </button>
        <button type="button" className="btn btn-primary">
          <OnEvent event='click' listener={createFilter} />
          Create Filter
        </button>
      </Socket>
    </Modal>
  </td>;

  function createFilter() {
    createStartDateFilter(
      nodes.startDate.value + 'T00:00:00',
      nodes.endDate.value + 'T23:59:59'
    );

    closeModal();

    $window.setTimeout(onFilterAdded);
  }

  function isModalOpen({filter: {createModal}}) {
    return createModal.open;
  }
}
