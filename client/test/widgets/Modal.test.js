import {jsx, Socket, $document} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {Modal, __set__, __ResetDependency__} from 'widgets/Modal';

describe('<Modal>', () => {
  let update;
  let $;
  let modalFct;
  let openFct;
  let closeFct;

  beforeEach(() => {
    modalFct = sinon.spy();
    $ = sinon.stub().returns({
      modal: modalFct,
      on: sinon.stub().callsArg(1)
    });
    __set__('$', $);
  });

  afterEach(() => {
    __ResetDependency__('$');
  });

  describe('open', () => {
    beforeEach(() => {
      openFct = sinon.stub().returns(true);
      closeFct = sinon.spy();

      ({update} = mountTemplate(
        <Modal isOpen={openFct} onClose={closeFct}>
          <Socket name="head"><span className="head">Header</span></Socket>
          <Socket name="body"><span className="body">Bodyer</span></Socket>
          <Socket name="foot"><span className="foot">Footer</span></Socket>
        </Modal>
      ));
      update({});
    });

    it('should add the head to the body', () => {
      const head = $document.body.querySelector('.head');

      expect(head).to.exist;
    });

    it('should add the body to the body', () => {
      const body = $document.body.querySelector('.body');

      expect(body).to.exist;
    });

    it('should add the foot to the body', () => {
      const foot = $document.body.querySelector('.foot');

      expect(foot).to.exist;
    });

    it('should initialize the modal', () => {
      expect(modalFct.called).to.eql(true);
    });

    it('should open the modal', () => {
      expect(modalFct.calledWith('show')).to.eql(true);
    });
  });

  describe('close', () => {
    beforeEach(() => {
      openFct = sinon.stub().returnsArg(0);
      closeFct = sinon.spy();

      ({update} = mountTemplate(
        <Modal isOpen={openFct} onClose={closeFct}>
          <Socket name="head"><span className="head">Header</span></Socket>
          <Socket name="body"><span className="body">Bodyer</span></Socket>
          <Socket name="foot"><span className="foot">Footer</span></Socket>
        </Modal>
      ));
      update(true);  // open the modal
      update(false); // close it again
    });

    it('should close the modal', () => {
      expect(modalFct.calledWith('hide')).to.eql(true);
    });

    it('should call the onClose function', () => {
      expect(closeFct.called).to.eql(true);
    });
  });
});
