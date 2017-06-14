import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {readFile, readFiles, __set__, __ResetDependency__} from 'utils/readFiles';

describe('utils readFiles', () => {
  let readFile;

  setupPromiseMocking();

  beforeEach(() => {
    readFile = (file) => Promise.resolve({file});
    __set__('readFile', readFile);
  });

  afterEach(() => {
    __ResetDependency__('readFile');
  });

  it('should return promise with content of all files passed to it', done => {
    const files = ['a.txt', 'b.txt'];

    readFiles(files)
      .then(contents => {
        files.forEach((file, index) => {
          expect(contents[index]).to.eql({file});
        });

        done();
      });

    Promise.runAll();
  });
});

describe('utils readFile', () => {
  const file = 'a.txt';
  let reader;
  let FileReader;

  setupPromiseMocking();

  beforeEach(() => {
    FileReader = function() {
      this.readAsText = sinon.spy();

      reader = this;
    };
    __set__('$window', {
      FileReader
    });
  });

  afterEach(() => {
    __ResetDependency__('$window');
  });

  it('should read file as text', () => {
    readFile(file);

    expect(reader.readAsText.calledWith(file)).to.eql(true);
  });

  it('should return successful promise when reader.onload is called', done => {
    const result = 'result-1';

    readFile(file)
      .then(response => {
        expect(response.file).to.eql(file);
        expect(response.content).to.eql(result);

        done();
      });

    reader.onload({
      target: {result}
    });

    Promise.runAll();
  });
});
