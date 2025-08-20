import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';

const broadcastSignalRequestBodySchema = z.object({
	signalName: z.string(),
	variables: z.record(z.string(), z.unknown()).optional(),
	tenantId: z.string().optional(),
});
type BroadcastSignalRequestBody = z.infer<typeof broadcastSignalRequestBodySchema>;

const broadcastSignalResponseBodySchema = z.object({
	tenantId: z.string(),
	signalKey: z.string(),
});
type BroadcastSignalResponseBody = z.infer<typeof broadcastSignalResponseBodySchema>;

const broadcastSignal: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/signals/broadcast`;
	},
};

export {broadcastSignal, broadcastSignalRequestBodySchema, broadcastSignalResponseBodySchema};
export type {BroadcastSignalRequestBody, BroadcastSignalResponseBody};
