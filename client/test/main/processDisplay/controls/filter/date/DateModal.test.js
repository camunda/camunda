import React from 'react';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {createReactMock} from 'testHelpers';
import sinon from 'sinon';
import {createDateModalReact, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/date/DateModal';
import {mount} from 'enzyme';
import moment from 'moment';
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
  let DateFields;
  let DateModal;

  beforeEach(() => {
    DateFields = createReactMock('DateFields');
    __set__('DateFields', DateFields);

    DateButton = createReactMock('DateButton', false, 'span');
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
    __ResetDependency__('DateFields');
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

    it('should close modal on hide', () => {
      const onHide = Modal.getProperty('onHide');

      Modal.reset();
      onHide();

      expect(Modal.calledWith({show: false})).to.eql(true);
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

    describe('body', () => {
      let bodyWrapper;

      beforeEach(() => {
        bodyWrapper = wrapper.find(Modal.Body);
      });

      it('should contain date fields', () => {
        expect(bodyWrapper.find(DateFields)).to.exist;
      });

      it('should pass onDateChange function to DateFields', () => {
        const onDateChange = DateFields.getProperty('onDateChange');

        DateFields.reset();
        onDateChange('startDate', moment([2014, 8, 12]));

        // once again stupid js 0 = january and so on
        expect(
          DateFields.getProperty('startDate').format('YYYY-MM-DD')
        ).to.eql('2014-09-12');
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

    describe('foot', () => {
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
