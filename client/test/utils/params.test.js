import {expect} from 'chai';
import {parseParams, stringifyParams} from 'utils';

describe('parseParams', () => {
  it('should be able to parse single query param', () => {
    expect(parseParams('?param1=something')).to.eql({
      param1: 'something'
    });
  });

  it('should be able to parse multiple query params', () => {
    expect(parseParams('?param1=something&param2=bonkers')).to.eql({
      param1: 'something',
      param2: 'bonkers' // I just love sound of this word, so back off mate
      // if you have something against it :) I will bite and scream in defense of it
    });
  });
});

describe('stringifyParams', () => {
  it('should stringify single query param', () => {
    expect(stringifyParams({
      a: 1
    })).to.eql('a=1');
  });

  it('should stringify multiple query params', () => {
    expect(stringifyParams({
      a: 1,
      b: 2
    })).to.eql('a=1&b=2');
  });

  it('should stringify multiple query params with excluded params', () => {
    expect(stringifyParams(
      {
        a: 1,
        b: 2,
        c: 3
      },
      ['b']
    )).to.eql('a=1&c=3');
  });
});
