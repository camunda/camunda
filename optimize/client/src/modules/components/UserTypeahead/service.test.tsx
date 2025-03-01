import {shallow} from 'enzyme';

import {get} from 'request';

import {
  getItems,
  getSelectedIdentity,
  getUser,
  getUserId,
  identityToItem,
  itemToElement,
  itemToString,
  searchIdentities,
} from './service';

jest.mock('request');
jest.mock('services', () => ({
  formatters: {
    getHighlightedText: jest.fn((text) => text),
  },
}));

describe('UserTypeahead service', () => {
  describe('searchIdentities', () => {
    it('should call the API and return the result', async () => {
      const mockResponse = {
        json: jest.fn().mockResolvedValue({total: 1, result: [{id: '1', name: 'John Doe'}]}),
      };
      (get as jest.Mock).mockResolvedValue(mockResponse);

      const result = await searchIdentities('John', false);

      expect(get).toHaveBeenCalledWith('api/identity/search', {
        terms: 'John',
        excludeUserGroups: false,
      });
      expect(result).toEqual({total: 1, result: [{id: '1', name: 'John Doe'}]});
    });
  });

  describe('getUser', () => {
    it('should call the API and return the user', async () => {
      const mockResponse = {json: jest.fn().mockResolvedValue({id: '1', name: 'John Doe'})};
      (get as jest.Mock).mockResolvedValue(mockResponse);

      const result = await getUser('1');

      expect(get).toHaveBeenCalledWith('api/identity/1');
      expect(result).toEqual({id: '1', name: 'John Doe'});
    });
  });

  describe('getUserId', () => {
    it('should return the id with USER: prefix if not already present', () => {
      expect(getUserId('123')).toBe('USER:123');
      expect(getUserId('USER:123')).toBe('USER:123');
      expect(getUserId(null)).toBe('USER:null');
    });
  });

  describe('itemToString', () => {
    it('should return the correct string representation of an item', () => {
      expect(itemToString({id: '1', label: 'John Doe', subText: 'john@example.com'})).toBe(
        'John Doe'
      );
      expect(itemToString({id: '2', label: '', subText: 'jane@example.com'})).toBe(
        'jane@example.com'
      );
      expect(itemToString({id: '3', label: '', subText: ''})).toBe('3');
      expect(itemToString(null)).toBe('');
    });
  });

  describe('itemToElement', () => {
    it('should render a loading skeleton when item id is "loading"', () => {
      const wrapper = shallow(
        itemToElement({id: 'loading', label: 'Loading...', disabled: true}, '')
      );
      expect(wrapper.find('.cds--skeleton')).toExist();
    });

    it('should render the item with highlighted text', () => {
      const wrapper = shallow(
        itemToElement({id: '1', label: 'John Doe', subText: 'john@example.com'}, 'John')
      );
      expect(wrapper.text()).toBe('John Doejohn@example.com');
      expect(wrapper.find('span[id="1"]')).toExist();
    });
  });

  describe('getItems', () => {
    it('should return a loading item when loading is true', () => {
      const result = getItems(true, 'John', [], [], [], [], false);
      expect(result).toEqual([{id: 'loading', label: 'John', disabled: true}]);
    });

    it('should filter out selected identities and return formatted items', () => {
      const identities = [
        {id: '1', name: 'John Doe', email: 'john@example.com'},
        {id: '2', name: 'Jane Smith', email: 'jane@example.com'},
      ];
      const users = [{id: 'USER:1', identity: identities[0]!}];
      const result = getItems(false, 'John', identities, [], users, [], false);
      expect(result).toEqual([
        {id: 'John', label: 'John'},
        {id: 'USER:2', label: 'Jane Smith', subText: 'jane@example.com'},
      ]);
    });
  });

  describe('identityToItem', () => {
    it('should convert an identity to an item', () => {
      const identity = {id: '1', name: 'John Doe', email: 'john@example.com'};
      const result = identityToItem(identity);
      expect(result).toEqual({
        id: 'USER:1',
        label: 'John Doe',
        subText: 'john@example.com',
      });
    });
  });

  describe('getSelectedIdentity', () => {
    it('should return the selected identity if it exists', () => {
      const identities = [
        {id: '1', name: 'John Doe', email: 'john@example.com'},
        {id: '2', name: 'Jane Smith', email: 'jane@example.com'},
      ];
      const users = [{id: 'USER:1', identity: identities[0]!}];
      const result = getSelectedIdentity('USER:2', identities, users, []);
      expect(result).toEqual(identities[1]);
    });

    it('should return null if the selected identity does not exist', () => {
      const result = getSelectedIdentity('USER:3', [], [], []);
      expect(result).toEqual({id: 'USER:3'});
    });

    it('should return null if id is null', () => {
      const result = getSelectedIdentity(null, [], [], []);
      expect(result).toBeUndefined();
    });
  });
});
