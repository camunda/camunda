import {updateMapOfInstances} from './service';

import {
  createArrayOfMockInstances,
  createMapOfMockInstances,
  createInstance
} from 'modules/testUtils';

describe('updateMapOfInstances', () => {
  let updatedMapOfInstances;

  it('should add new instance to the map', () => {
    //given
    const workflowInstances = createArrayOfMockInstances(5);
    const mapOfInstances = createMapOfMockInstances(workflowInstances);

    //when
    const newWorkflowInstances = createArrayOfMockInstances(3);
    updatedMapOfInstances = updateMapOfInstances(
      newWorkflowInstances,
      mapOfInstances
    );

    //then
    expect(updatedMapOfInstances.size).toBe(8);
  });

  it('should update values of existing map elements', () => {
    //given
    //default value for mocked instance state is 'ACTIVE';
    const workflowInstances = createArrayOfMockInstances(1);
    const mapOfInstances = createMapOfMockInstances(workflowInstances);

    const newInstanceState = 'COMPLETED';
    const newWorkflowInstances = [
      createInstance({id: 'id_0', state: newInstanceState})
    ];

    //when
    updatedMapOfInstances = updateMapOfInstances(
      newWorkflowInstances,
      mapOfInstances
    );

    //then
    expect(updatedMapOfInstances.get('id_0').state).toBe(newInstanceState);
  });

  it('should simply append new items in the order they are added to the array.', () => {
    //given
    const workflowInstances = [
      createInstance({id: 'a_Id'}),
      createInstance({id: 'b_Id'})
    ];
    const mapOfInstances = createMapOfMockInstances(workflowInstances);
    const newWorkflowInstances = [
      createInstance({id: 'd_Id'}),
      createInstance({id: 'c_Id'})
    ];

    //when
    updatedMapOfInstances = updateMapOfInstances(
      newWorkflowInstances,
      mapOfInstances
    );

    //then
    expect([...updatedMapOfInstances.keys()]).toEqual([
      'a_Id',
      'b_Id',
      'd_Id',
      'c_Id'
    ]);
  });

  it('should return a map with a maximum of 10 elements', () => {
    //given
    const workflowInstances = createArrayOfMockInstances(5);
    const mapOfInstances = createMapOfMockInstances(workflowInstances);

    //when
    const newWorkflowInstances = createArrayOfMockInstances(20);
    updatedMapOfInstances = updateMapOfInstances(
      newWorkflowInstances,
      mapOfInstances
    );

    //then
    expect(updatedMapOfInstances.size).toBe(10);
  });
});
