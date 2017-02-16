import {jsx, OnEvent, Socket, createReferenceComponent, $window, Scope} from 'view-utils';
import {openModal, closeModal, createStartDateFilter, formatDate} from './service';
import {Dropdown, DropdownItem, Modal} from 'widgets';
import {DateButton, TODAY, YESTERDAY, PAST7, PAST30,
        LAST_WEEK, LAST_MONTH, LAST_YEAR,
        THIS_WEEK, THIS_MONTH, THIS_YEAR} from './DateButton';

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
                <DateButton dateLabel={TODAY} />
                <DateButton dateLabel={YESTERDAY} />
                <DateButton dateLabel={PAST7} />
                <DateButton dateLabel={PAST30} />
              </p>
              <p className="three-button-row">
                <DateButton dateLabel={LAST_WEEK} />
                <DateButton dateLabel={LAST_MONTH} />
                <DateButton dateLabel={LAST_YEAR} />
              </p>
              <p className="three-button-row">
                <DateButton dateLabel={THIS_WEEK} />
                <DateButton dateLabel={THIS_MONTH} />
                <DateButton dateLabel={THIS_YEAR} />
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
