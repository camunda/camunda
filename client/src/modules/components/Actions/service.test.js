import {STATE, OPERATION_TYPE} from 'modules/constants';
import {wrapIdinQuery, isWithIncident, isRunning} from './service';

const mockIncidentInstance = {
  id: '8590375632-2',
  state: 'INCIDENT',
  incidents: [{state: 'ACTIVE'}]
};

const mockActiveInstance = {
  id: '8590375632-2',
  state: 'ACTIVE'
};

const mockIncidentQuery = [
  {ids: ['8590375632-2'], incidents: true, running: true}
];

const mockActiveQuery = [{ids: ['8590375632-2'], active: true, running: true}];

describe('Action services', () => {
  describe('Action Buttons', () => {
    describe('isWithIncident', () => {
      it('should return true if an instance has an incident', () => {
        expect(isWithIncident(mockIncidentInstance)).toBe(true);
      });

      it('should return false if an instance is active', () => {
        expect(isWithIncident(mockActiveInstance)).toBe(false);
      });
    });

    describe('wrapIdinQuery', () => {
      it('should return cancel-query for active instance', () => {
        expect(
          wrapIdinQuery(
            OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE,
            mockActiveInstance
          )
        ).toEqual(mockActiveQuery);
      });

      it('should return cancel-query for instance with incident ', () => {
        expect(
          wrapIdinQuery(
            OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE,
            mockIncidentInstance
          )
        ).toEqual(mockIncidentQuery);
      });

      it('should return retry-query for instance with incidents', () => {
        expect(
          wrapIdinQuery(OPERATION_TYPE.UPDATE_JOB_RETRIES, mockIncidentInstance)
        ).toEqual(mockIncidentQuery);
      });
    });

    describe('isRunning', () => {
      const mockCompletedInstance = {
        id: '8590375632-2',
        state: STATE.COMPLETED
      };
      const mockCanceldInstance = {
        id: '8590375632-2',
        state: STATE.CANCELED
      };

      it('should return true if an instance is running', () => {
        expect(isRunning(mockIncidentInstance)).toBe(true);
        expect(isRunning(mockActiveInstance)).toBe(true);

        expect(isRunning(mockCompletedInstance)).toBe(false);
        expect(isRunning(mockCanceldInstance)).toBe(false);
      });
    });
  });
});
