import {expect} from 'chai';
import {reducer, createSetVersionAction} from 'main/processSelection/reducer';

describe('processSelection reducer', () => {
  const key = 'key-1';
  const version = 3;
  const xml = 'xml';
  let inputState;

  beforeEach(() => {
    inputState = {
      processDefinitions: {
        data: [
          {
            current: {
              key
            },
            versions: [
              {
                version: 3442
              },
              {
                version,
                a: 1
              }
            ]
          }
        ]
      }
    };
  });

  it('should be possible to set version in selected process definition', () => {
    const resultState = reducer(
      inputState,
      createSetVersionAction(key, version)
    );

    expect(resultState).to.eql({
      processDefinitions: {
        data: [
          {
            current: {
              version,
              a: 1
            },
            versions: [
              {
                version: 3442
              },
              {
                version,
                a: 1
              }
            ]
          }
        ]
      }
    });
  });

  it('should be possible to set version in selected process definition with xml', () => {
    const resultState = reducer(
      inputState,
      createSetVersionAction(key, version, xml)
    );

    expect(resultState).to.eql({
      processDefinitions: {
        data: [
          {
            current: {
              version,
              bpmn20Xml: xml,
              a: 1
            },
            versions: [
              {
                version: 3442
              },
              {
                version,
                a: 1
              }
            ]
          }
        ]
      }
    });
  });
});
