import React from 'react';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {createReactMock} from 'testHelpers';
import sinon from 'sinon';
import {createDateModalReact, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/date/DateModal';
import {mount} from 'enzyme';
import {TODAY, YESTERDAY, PAST7, PAST30,
        LAST_WEEK, LAST_MONTH, LAST_YEAR,
        THIS_WEEK, THIS_MONTH, THIS_YEAR} from 'main/processDisplay/controls/filter/date/DateButton';

chai.use(chaiEnzyme());

const jsx = React.createElement;
const {expect} = chai;

describe('<DateModal>', () => {
  let wrapper;
  let Modal;
  let createStartDateFilter;
  let onNextTick;
  let createCallback;
  let DateButton;
  let DatePicker;
  let DateModal;

  beforeEach(() => {
    DatePicker = createReactMock('DatePicker');
    __set__('DatePicker', DatePicker);

    DateButton = createReactMock('DateButton');
    __set__('DateButton', DateButton);

    Modal = createReactMock('Modal', true);
    __set__('Modal', Modal);

    Modal.Header = createReactMock('Header', true);
    Modal.Body = createReactMock('Body', true);
    Modal.Footer = createReactMock('Body', true);

    createStartDateFilter = sinon.spy();
    __set__('createStartDateFilter', createStartDateFilter);

    onNextTick = sinon.stub().callsArg(0);
    __set__('onNextTick', onNextTick);

    createCallback = sinon.spy();

    DateModal = createDateModalReact(createCallback);

    wrapper = mount(<DateModal />);
  });

  afterEach(() => {
    __ResetDependency__('DatePicker');
    __ResetDependency__('DateButton');
    __ResetDependency__('Modal');
    __ResetDependency__('createStartDateFilter');
    __ResetDependency__('onNextTick');
  });

  describe('Modal', () => {
    let modalWrapper;

    beforeEach(() => {
      modalWrapper = wrapper.find(Modal);
    });

    it('should be included', () => {
      expect(wrapper).to.contain.text(Modal.text);
    });

    describe('head', () => {
      let headWrapper;

      beforeEach(() => {
        headWrapper = wrapper.find(Modal.Header);
      });

      it('should contain button that closes modal', () => {
        const button = headWrapper.find('button.close');

        expect(button).to.exist;

        button.simulate('click');

        expect(modalWrapper).to.have.prop('show', false);
      });

      it('should contain title', () => {
        expect(headWrapper.find('h4.modal-title')).to.exist;
      });
    });

    describe('body socket', () => {
      let bodyWrapper;

      beforeEach(() => {
        bodyWrapper = wrapper.find(Modal.Body);
      });

      it('should contain start date field', () => {
        expect(bodyWrapper.find(DatePicker)).to.exist;
      });

      it('should contain date buttons', () => {
        expect(bodyWrapper.find(DateButton)).to.exist;

        [
          TODAY, YESTERDAY, PAST7, PAST30,
          LAST_WEEK, LAST_MONTH, LAST_YEAR,
          THIS_WEEK, THIS_MONTH, THIS_YEAR
        ].forEach(label => {
          const call = DateButton.calls.find(([{dateLabel}]) => dateLabel === label);

          expect(call).to.exist;
        });
      });
    });

    describe('foot socket', () => {
      let footWrapper;

      beforeEach(() => {
        footWrapper = wrapper.find(Modal.Footer);
      });

      it('should have close button that closes modal', () => {
        const closeBtn = footWrapper
          .find('button')
          .filterWhere(btn => btn.text().indexOf('Abort') >= 0);

        closeBtn.simulate('click');

        expect(closeBtn).to.exist;
        expect(modalWrapper).to.have.prop('show', false);
      });

      it('should have create filter button that closes modal', () => {
        const createBtn = footWrapper
          .find('button')
          .filterWhere(btn => btn.text().indexOf('Create Filter') >= 0);

        createBtn.simulate('click');

        expect(createBtn).to.exist;
        expect(modalWrapper).to.have.prop('show', false);
        expect(createStartDateFilter.calledOnce).to.eql(true, 'expected date filter to be created');
        expect(createCallback.calledOnce).to.eql(true, 'expected onFilterAdded callback to be called');
      });
    });
  });
});
