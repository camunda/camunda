import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {addModuleLoader, getModule, onModuleLoaded} from 'dynamicLoader/registry.service';

describe('dynamic loader registry service', () => {
  const name = 'name1';
  const module = 'module1';

  setupPromiseMocking();

  describe('getModule', () => {
    let loader;

    beforeEach(() => {
      loader = sinon.stub().returns(
        Promise.resolve(module)
      );

      addModuleLoader(name, loader);
    });

    it('should start loader added by addModuleLoader', (done) => {
      getModule(name)
        .then(actualModule => {
          expect(actualModule).to.eql(module);

          done();
        });

      Promise.runAll();
    });

    it('should load module only once', (done) => {
      getModule(name)
        .then(getModule.bind(null, name))
        .then(() => done());

      Promise.runAll();

      expect(loader.calledOnce).to.eql(true);
    });

    describe('onModuleLoaded', () => {
      it('should not start loader on its own', () => {
        onModuleLoaded(name);

        expect(loader.called).to.eql(false);
      });

      it('should resolve promise when getModule loads module', (done) => {
        onModuleLoaded(name)
          .then(actualModule => {
            expect(actualModule).to.eql(module);

            done();
          });

        getModule(name);
        Promise.runAll();
      });
    });
  });
});
