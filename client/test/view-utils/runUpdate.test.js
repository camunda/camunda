import {expect} from 'chai';
import sinon from 'sinon';
import {runUpdate} from 'view-utils';

describe('runUpdate', () => {
  const state = 'state';

  it('should call update function with state', () => {
    const update = sinon.spy();

    runUpdate(update, state);

    expect(update.calledWith(state)).to.eql(true);
  });

  it('should call every function in update array', () => {
    const update = [
      sinon.spy(),
      sinon.spy()
    ];

    runUpdate(update, state);

    assertArray(update, (update, path) => {
      expect(update.calledWith(state)).to.eql(true, `expected position ${path.join(', ')} to be called with state`);
    });
  });

  it('should call update method', () => {
    const update = {
      update: sinon.spy()
    };

    runUpdate(update, state);

    expect(update.update.calledWith(state)).to.eql(true);
  });

  it('should handle nested arrays', () => {
    const update = [
      sinon.spy(),
      sinon.spy(),
      [
        sinon.spy(),
        sinon.spy()
      ]
    ];

    runUpdate(update, state);

    assertArray(update, (update, path) => {
      expect(update.calledWith(state)).to.eql(true, `expected position ${path.join(', ')} to be called with state`);
    });
  });

  it('should handle nested arrays with objects', () => {
    const update = [
      sinon.spy(),
      sinon.spy(),
      [
        sinon.spy(),
        {
          update: sinon.spy()
        },
        {
          update: [
            sinon.spy()
          ]
        }
      ]
    ];

    runUpdate(update, state);

    function assertion(update, path) {
      if (typeof update === 'function') {
        expect(update.calledWith(state)).to.eql(true, `expected position ${path.join(', ')} to be called with state`);
      } else if (Array.isArray(update.update)) {
        assertArray(update.update, assertion, path);
      } else {
        expect(update.update.calledWith(state)).to.eql(true);
      }
    }

    assertArray(update, assertion);
  });
});

function assertArray(array, assertion, path = []) {
  array.forEach((item, index) => {
    const itemPath = path.concat(index);

    if (Array.isArray(item)) {
      assertArray(item, assertion, itemPath);
    } else {
      assertion(item, itemPath);
    }
  });
}
