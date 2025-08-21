import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';

const pinClockRequestBodySchema = z.object({
	timestamp: z.number().int(),
});
type PinClockRequestBody = z.infer<typeof pinClockRequestBodySchema>;

const pinClock: Endpoint = {
	method: 'PUT',
	getUrl: () => `/${API_VERSION}/clock`,
};

const resetClock: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/clock/reset`,
};

export {pinClockRequestBodySchema, pinClock, resetClock};

export type {PinClockRequestBody};
