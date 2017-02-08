import {jsx, OnEvent, Socket} from 'view-utils';
import {openModal, closeModal} from './service';
import {Dropdown, DropdownItem, Modal} from 'widgets';

export function FilterCreation() {
  return <td>
    <Dropdown>
      <Socket name="label">
        + <span className="caret"></span>
      </Socket>
      <Socket name="list">
        <DropdownItem>
          <OnEvent event={['click']} listener={openModal} />
          Start Date
        </DropdownItem>
      </Socket>
    </Dropdown>
    <Modal open={isModalOpen} onClose={closeModal}>
      <Socket name="head">
        <button type="button" className="close">
          <OnEvent event={['click']} listener={closeModal} />
          <span aria-hidden="true">×</span>
        </button>
        <h4 className="modal-title" id="exampleModalLabel">New Filter</h4>
      </Socket>
      <Socket name="body">
        <form lpformnum="2">
          <label>Start Date Filter:</label>
          <center>
            <div className="input-group input-daterange" id="filter-datepicker" style="margin-bottom: 30px;">
              <input type="text" className="form-control start" value="2016-12-06" />
              <span className="input-group-addon">to</span>
              <input type="text" className="form-control end" value="2016-12-06" />
            </div>
          </center>
          <div className="form-group" id="filter-freqently-used-dates">
            <label>Frequently Used:</label>
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
          <OnEvent event={['click']} listener={closeModal} />
          Abort
          </button>
        <button type="button" className="btn btn-primary" id="create-filter-button">Create Filter</button>
      </Socket>
    </Modal>
  </td>;

  function isModalOpen({createFilter}) {
    return createFilter.open;
  }
}
