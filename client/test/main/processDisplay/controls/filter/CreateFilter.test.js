import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent, triggerEvent, selectByText} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {CreateFilter, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/CreateFilter';
import {TODAY, YESTERDAY, PAST7, PAST30,
        LAST_WEEK, LAST_MONTH, LAST_YEAR,
        THIS_WEEK, THIS_MONTH, THIS_YEAR} from 'main/processDisplay/controls/filter/DateButton';

describe('<CreateFilter>', () => {
  let node;
  let update;
  let Dropdown;
  let Modal;
  let createModal;
  let Socket;
  let DropdownItem;
  let createStartDateFilter;
  let onNextUpdate;
  let onFilterAdded;
  let $;
  let datepickerFct;
  let DateButton;

  beforeEach(() => {
    Dropdown = createMockComponent('Dropdown', true);
    __set__('Dropdown', Dropdown);

    DateButton = createMockComponent('DateButton');
    __set__('DateButton', DateButton);

    Modal = createMockComponent('Modal', true);
    Modal.open = sinon.spy();
    Modal.close = sinon.spy();
    createModal = sinon.stub().returns(Modal);
    __set__('createModal', createModal);

    Socket = createMockComponent('Socket', true);
    __set__('Socket', Socket);

    DropdownItem = createMockComponent('DropdownItem', true);
    __set__('DropdownItem', DropdownItem);

    createStartDateFilter = sinon.spy();
    __set__('createStartDateFilter', createStartDateFilter);

    onNextUpdate = sinon.stub().callsArg(0);
    __set__('onNextUpdate', onNextUpdate);

    onFilterAdded = sinon.spy();

    datepickerFct = sinon.spy();
    $ = sinon.stub().returns({
      datepicker: datepickerFct
    });
    __set__('$', $);

    ({node, update} = mountTemplate(<CreateFilter onFilterAdded={onFilterAdded}/>));
  });

  afterEach(() => {
    __ResetDependency__('Dropdown');
    __ResetDependency__('DateButton');
    __ResetDependency__('createModal');
    __ResetDependency__('Socket');
    __ResetDependency__('DropdownItem');
    __ResetDependency__('createStartDateFilter');
    __ResetDependency__('onNextUpdate');
    __ResetDependency__('$');
  });

  it('should initialize the datepicker', () => {
    expect(datepickerFct.calledOnce).to.eql(true);
  });

  describe('Dropdown', () => {
    it('should be included', () => {
      expect(node.textContent).to.contain(Dropdown.text);
    });

    it('should contain label socket', () => {
      const labelNode = Socket.getChildrenNode({name: 'label'});

      expect(
        Dropdown.getChildTemplate({attributes: {name: 'label'}, text: 'Socket'})
      ).to.exist;
      expect(labelNode).to.contain.text('+');
      expect(labelNode.querySelector('.caret')).to.exist;
    });

    it('should contain list socket', () => {
      const listNode = Socket.getChildrenNode({name: 'list'});

      expect(
        Dropdown.getChildTemplate({attributes: {name: 'list'}, text: 'Socket'})
      ).to.exist;
      expect(listNode).to.contain.text('Start Date');
    });

    it('should contain DropdownItem that opens modal', () => {
      expect(DropdownItem.getAttribute('listener')).to.equal(Modal.open);
    });
  });

  describe('Modal', () => {
    it('should be included', () => {
      expect(node.textContent).to.contain(Modal.text);
    });

    describe('head socket', () => {
      let headNode;

      beforeEach(() => {
        headNode = Socket.getChildrenNode({name: 'head'});
      });

      it('should be included', () => {
        expect(
          Modal.getChildTemplate({attributes: {name: 'head'}, text: 'Socket'})
        ).to.exist;
      });

      it('should contain button that closes modal', () => {
        const button = headNode.querySelector('button.close');

        expect(button).to.exist;

        triggerEvent({
          node: button,
          eventName: 'click'
        });

        expect(Modal.close.calledOnce).to.eql(true);
      });

      it('should contain title', () => {
        expect(headNode.querySelector('h4.modal-title')).to.exist;
      });
    });

    describe('body socket', () => {
      let bodyNode;
      let startDate;
      let endDate;

      beforeEach(() => {
        bodyNode = Socket.getChildrenNode({name: 'body'});
        startDate = bodyNode.querySelector('input[type="text"].form-control.start');
        endDate = bodyNode.querySelector('input[type="text"].form-control.end');

        update({});
      });

      it('should be included', () => {
        expect(
          Modal.getChildTemplate({attributes: {name: 'body'}, text: 'Socket'})
        ).to.exist;
      });

      it('should contain start date field', () => {
        expect(startDate).to.exist;
      });

      it('should contain end date field', () => {
        expect(endDate).to.exist;
      });

      it('should contain date buttons', () => {
        expect(bodyNode.textContent).to.include(DateButton.text);
        expect(DateButton.appliedWith({dateLabel: TODAY})).to.eql(true);
        expect(DateButton.appliedWith({dateLabel: YESTERDAY})).to.eql(true);
        expect(DateButton.appliedWith({dateLabel: PAST7})).to.eql(true);
        expect(DateButton.appliedWith({dateLabel: PAST30})).to.eql(true);
        expect(DateButton.appliedWith({dateLabel: LAST_WEEK})).to.eql(true);
        expect(DateButton.appliedWith({dateLabel: LAST_MONTH})).to.eql(true);
        expect(DateButton.appliedWith({dateLabel: LAST_YEAR})).to.eql(true);
        expect(DateButton.appliedWith({dateLabel: THIS_WEEK})).to.eql(true);
        expect(DateButton.appliedWith({dateLabel: THIS_MONTH})).to.eql(true);
        expect(DateButton.appliedWith({dateLabel: THIS_YEAR})).to.eql(true);
      });
    });

    describe('foot socket', () => {
      let footNode;

      beforeEach(() => {
        footNode = Socket.getChildrenNode({name: 'foot'});
      });

      it('should be included', () => {
        expect(
          Modal.getChildTemplate({attributes: {name: 'foot'}, text: 'Socket'})
        ).to.exist;
      });

      it('should have close button that closes modal', () => {
        const closeBtn = selectByText(
          footNode.querySelectorAll('button'),
          'Abort'
        )[0];

        triggerEvent({
          node: closeBtn,
          eventName: 'click'
        });

        expect(closeBtn).to.exist;
        expect(Modal.close.calledOnce).to.eql(true, 'expected modal to be closed');
      });

      it('should have close button that closes modal', () => {
        const closeBtn = selectByText(
          footNode.querySelectorAll('button'),
          'Abort'
        )[0];

        triggerEvent({
          node: closeBtn,
          eventName: 'click'
        });

        expect(closeBtn).to.exist;
        expect(Modal.close.calledOnce).to.eql(true, 'expected modal to be closed');
      });

      it('should have create filter button that closes modal', () => {
        const createBtn = selectByText(
          footNode.querySelectorAll('button'),
          'Create Filter'
        )[0];

        triggerEvent({
          node: createBtn,
          eventName: 'click'
        });

        expect(createBtn).to.exist;
        expect(Modal.close.calledOnce).to.eql(true, 'expected modal to be closed');
        expect(createStartDateFilter.calledOnce).to.eql(true, 'expected date filter to be created');
        expect(onFilterAdded.calledOnce).to.eql(true, 'expected onFilterAdded callback to be called');
      });
    });
  });
});
