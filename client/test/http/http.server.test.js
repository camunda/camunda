import {expect} from 'chai';
import {setupPromiseMocking} from 'testHelpers';
import sinon from 'sinon';
import {get, post, put, request, formatQuery, __set__, __ResetDependency__} from 'http/http.service';

describe('http service', () => {
  setupPromiseMocking();

  describe('request', () => {
    const url = 'https://hanka.grzeska.nie.lubi.com';
    const method = 'GET';

    let $fetch;

    beforeEach(() => {
      $fetch = sinon.stub();

      __set__('$fetch', $fetch);
    });

    afterEach(() => {
      __ResetDependency__('$fetch');
    });

    it('should open http request with given method and url', () => {
      request({
        url,
        method
      });

      const {method: actualMethod} = $fetch.firstCall.args[1];

      expect($fetch.calledWith(url)).to.eql(true);
      expect(actualMethod).to.eql(method);
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

      const {headers: {g}} = $fetch.firstCall.args[1];

      expect(g).to.eql(headers.g);
    });

    it('should set default Content-Type to application/json', () => {
      request({
        url,
        method
      });

      const {headers: {'Content-Type': contentType}} = $fetch.firstCall.args[1];

      expect(contentType).to.eql('application/json');
    });

    it('should provide option to override Content-Type header', () => {
      const contentType = 'text';

      request({
        url,
        method,
        headers: {
          'Content-Type': contentType
        }
      });

      const {headers: {'Content-Type': actualContentType}} = $fetch.firstCall.args[1];

      expect(actualContentType).to.eql(contentType);
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

      const {body: actualBody} = $fetch.firstCall.args[1];

      expect(actualBody).to.eql(JSON.stringify(body));
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
