import {post} from 'modules/request';

import {login} from './api';

jest.mock('modules/request');

describe('login api', () => {
  beforeEach(() => {
    post.mockClear();
  });

  describe('login', () => {
    it('should call post with username and password', () => {
      // given
      const username = 'foo';
      const password = 'bar';

      // when
      login({username, password});

      // then
      expect(post.mock.calls[0][0]).toBe('/login');
      expect(post.mock.calls[0][1]).toBe('username=foo&password=bar');
      expect(post.mock.calls[0][2]).toEqual({
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        }
      });
    });
  });
});
