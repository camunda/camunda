import {jsx, OnEvent, Socket, createReferenceComponent, $window, Scope} from 'view-utils';
import {openModal, closeModal, createStartDateFilter, formatDate} from './service';
import {Dropdown, DropdownItem, Modal} from 'widgets';
import {DateButton} from './DateButton';

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
              <input type="text" className="form-control start" value={currentDate()}>
                <Reference name="startDate" />
              </input>
              <span className="input-group-addon">to</span>
              <input type="text" className="form-control end" value={currentDate()}>
                <Reference name="endDate" />
              </input>
            </div>
          </center>
          <Scope selector={getDateNodes}>
            <div className="form-group">
              <span className="label">Frequently Used:</span>
              <p className="four-button-row">
                <DateButton date="Today" />
                <DateButton date="Yesterday" />
                <DateButton date="Past 7 days" />
                <DateButton date="Past 30 days" />
              </p>
              <p className="three-button-row">
                <DateButton date="Last Week" />
                <DateButton date="Last Month" />
                <DateButton date="Last Year" />
              </p>
              <p className="three-button-row">
                <DateButton date="This Week" />
                <DateButton date="This Month" />
                <DateButton date="This Year" />
              </p>
            </div>
          </Scope>
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

  function getDateNodes() {
    return {
      start: nodes.startDate,
      end: nodes.endDate
    };
  }

  function currentDate() {
    return formatDate(new Date());
  }

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
