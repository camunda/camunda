import {expect} from 'chai';
import sinon from 'sinon';
import {formatDate} from 'utils';
import {setupPromiseMocking} from 'testHelpers';
import {
  checkLicense, uploadLicense, checkLicenseAndNotifyIfExpiresSoon,
  __set__, __ResetDependency__
} from 'license/service';

describe('license checkLicenseAndNotifyIsExpiresSoon', () => {
  let get;
  let addNotification;
  let response;

  setupPromiseMocking();

  beforeEach(() => {
    response = {
      json: sinon.stub().returnsThis(),
      validUntil: '9999-12-25'
    };

    get = sinon.stub().returns(Promise.resolve(response));
    __set__('get', get);

    addNotification = sinon.spy();
    __set__('addNotification', addNotification);
  });

  afterEach(() => {
    __ResetDependency__('get');
    __ResetDependency__('addNotification');
  });

  it('should not add notification if license expires later than 10 days from now', done => {
    checkLicenseAndNotifyIfExpiresSoon()
      .then(() => {
        expect(addNotification.called).to.eql(false);

        done();
      });

    Promise.runAll();
  });

  it('should add notification if license expires within 10 days', done => {
    const now = new Date();
    const in4Days = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 4);

    response.validUntil = formatDate(in4Days);

    checkLicenseAndNotifyIfExpiresSoon()
      .then(() => {
        expect(
          addNotification.called
        ).to.eql(true);

        done();
      });

    Promise.runAll();
  });
});

describe('license checkLicense', () => {
  let get;

  setupPromiseMocking();

  beforeEach(() => {
    get = sinon.stub().returns(Promise.resolve({
      json: sinon.stub().returnsThis(),
      valid: true
    }));
    __set__('get', get);
  });

  afterEach(() => {
    __ResetDependency__('get');
  });

  it('should validate license with get request', done => {
    checkLicense().then(response => {
      expect(response.valid).to.eql(true);
      expect(get.calledWith('/api/license/validate')).to.eql(true);

      done();
    });

    Promise.runAll();
  });
});

describe('license uploadLicense', () => {
  let post;

  setupPromiseMocking();

  beforeEach(() => {
    post = sinon.stub().returns(Promise.resolve({
      json: sinon.stub().returnsThis(),
      valid: true
    }));
    __set__('post', post);
  });

  afterEach(() => {
    __ResetDependency__('post');
  });

  it('should upload new license key', done => {
    const key = 'license key';

    uploadLicense(key).then(response => {
      expect(response.valid).to.eql(true);
      expect(
        post.calledWith(
          '/api/license/validate-and-store',
          key,
          {
            headers: {
              'Content-Type': 'text/plain'
            }
          }
        )
      ).to.eql(true);

      done();
    });

    Promise.runAll();
  });
});
