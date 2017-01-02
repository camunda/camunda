import {expect} from 'chai';
import {setupPromiseMocking} from 'testHelpers';
import sinon from 'sinon';
import {get, post, put, request, formatQuery, __set__, __ResetDependency__} from 'http/http.service';

describe('http service', () => {
  setupPromiseMocking();

  describe('request', () => {
    const url = 'https://hanka.grzeska.nie.lubi.com';
    const method = 'GET';
    const responseText = 'response text';

    let $XMLHttpRequest;
    let http;

    beforeEach(() => {
      $XMLHttpRequest = function() {
        http = this;

        this.open = sinon.spy();
        this.send = sinon.spy();
        this.setRequestHeader = sinon.spy();
        this.readyState = $XMLHttpRequest.DONE;
        this.status = 200;
        this.responseText = responseText;
      };
      $XMLHttpRequest.DONE = 'DONE';

      __set__('$XMLHttpRequest', $XMLHttpRequest);
    });

    afterEach(() => {
      __ResetDependency__('$XMLHttpRequest');
    });

    it('should open http request with given method and url', () => {
      request({
        url,
        method
      });

      expect(http.open.calledWith(method, url, true)).to.eql(true);
    });

    it('should set headers', () => {
      const headers = {
        g: 1
      };

      request({
        url,
        method,
        headers
      });

      expect(http.setRequestHeader.calledWith('g', 1)).to.eql(true);
    });

    it('should set default Content-Type to application/json', () => {
      request({
        url,
        method
      });

      expect(http.setRequestHeader.calledWith('Content-Type', 'application/json')).to.eql(true);
    });

    it('should provide option to override Content-Type header', () => {
      const ContentType = 'text';

      request({
        url,
        method,
        headers: {
          'Content-Type': ContentType
        }
      });

      expect(http.setRequestHeader.calledWith('Content-Type', ContentType)).to.eql(true);
    });

    it('should stringify json body objects', () => {
      const body = {
        d: 1
      };

      request({
        url,
        method,
        body
      });

      expect(http.send.calledWith(JSON.stringify(body))).to.eql(true);
    });

    it('should return promise with responseText', (done) => {
      request({
        url,
        method
      }).then(response =>{
        expect(response).to.eql(responseText);

        done();
      });

      http.onreadystatechange();
      Promise.runAll();
    });

    it('should reject response if status is wrong', (done) => {
      $XMLHttpRequest = function() {
        http = this;

        this.open = sinon.spy();
        this.send = sinon.spy();
        this.setRequestHeader = sinon.spy();
        this.readyState = $XMLHttpRequest.DONE;
        this.status = 404;
        this.responseText = responseText;
      };
      $XMLHttpRequest.DONE = 'DONE';

      __set__('$XMLHttpRequest', $XMLHttpRequest);

      request({
        url,
        method
      }).catch(({status, response}) =>{
        expect(response).to.eql(responseText);
        expect(status).to.eql(404);

        done();
      });

      http.onreadystatechange();
      Promise.runAll();
    });
  });

  describe('formatQuery', () => {
    it('should format query object into proper query string', () => {
      const query = {
        a: 1,
        b: '5=5'
      };

      expect(formatQuery(query)).to.eql('a=1&b=5%3D5');
    });
  });

  describe('methods shortcuts functions', () => {
    const response = 'response1';
    const url = 'http://basia-barbara-buda.eu';
    let request;

    beforeEach(() => {
      request = sinon
        .stub()
        .returns(response);

      __set__('request', request);
    });

    afterEach(() => {
      __ResetDependency__('request');
    });

    describe('put', () => {
      const body = 'hot-naked-body';

      it('should call request with correct options', () => {
        put(url, body);

        expect(request.calledWith({
          url,
          body,
          method: 'POST'
        }));
      });

      it('should use custom options', () => {
        put(url, body, {
          d: 12
        });

        expect(request.calledWith({
          url,
          body,
          method: 'POST',
          d: 12
        }));
      });

      it('should return request response', () => {
        expect(put()).to.eql(response);
      });
    });

    describe('post', () => {
      const body = 'hot-naked-body';

      it('should call request with correct options', () => {
        post(url, body);

        expect(request.calledWith({
          url,
          body,
          method: 'POST'
        }));
      });

      it('should use custom options', () => {
        post(url, body, {
          d: 12
        });

        expect(request.calledWith({
          url,
          body,
          method: 'POST',
          d: 12
        }));
      });

      it('should return request response', () => {
        expect(post()).to.eql(response);
      });
    });

    describe('get', () => {
      const query = 'q';

      it('should call request with correct options', () => {
        get(url, query);

        expect(request.calledWith({
          url,
          query,
          method: 'GET'
        }));
      });

      it('should use custom options', () => {
        const options = {
          a: 1
        };

        get(url, query, options);

        expect(request.calledWith({
          url,
          query,
          method: 'GET',
          a: 1
        }));
      });

      it('should return request response', () => {
        expect(get()).to.eql(response);
      });
    });
  });
});
