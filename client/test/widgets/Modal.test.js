import {jsx, Socket, $document} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {createModal, __set__, __ResetDependency__} from 'widgets/Modal';

describe('<Modal>', () => {
  let update;
  let $;
  let modalFct;
  let Modal;

  beforeEach(() => {
    modalFct = sinon.spy();
    $ = sinon.stub().returns({
      modal: modalFct,
      on: sinon.stub().callsArg(1)
    });
    __set__('$', $);

    Modal = createModal();
    ({update} = mountTemplate(
      <Modal>
        <Socket name="head"><span className="head">Header</span></Socket>
        <Socket name="body"><span className="body">Bodyer</span></Socket>
        <Socket name="foot"><span className="foot">Footer</span></Socket>
      </Modal>
    ));
    update({});
  });

  afterEach(() => {
    __ResetDependency__('$');
  });

  describe('initial', () => {
    it('should initialize the modal', () => {
      expect(modalFct.called).to.eql(true);
    });
  });

  describe('open', () => {
    beforeEach(() => {
      Modal.open();
    });

    it('should add itself to the body', () => {
      expect($document.body.querySelector('.modal')).to.exist;
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

    it('should open the modal', () => {
      expect(modalFct.calledWith('show')).to.eql(true);
    });
  });

  describe('close', () => {
    beforeEach(() => {
      Modal.open();  // open the modal
      Modal.close(); // close it again
    });

    it('should close the modal', () => {
      expect(modalFct.calledWith('hide')).to.eql(true);
    });
  });
});
