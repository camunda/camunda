import {defineConfig, mergeConfig, type Plugin} from 'vite';
import base from './vite.config';

const target = 'http://localhost:8190';

const readBody = (req: NodeJS.ReadableStream) =>
	new Promise<string>((resolve) => {
		const chunks: Buffer[] = [];
		req.on('data', (chunk) => chunks.push(Buffer.from(chunk)));
		req.on('end', () => resolve(Buffer.concat(chunks).toString()));
	});

const cookieHeader = (setCookies: string[]) => setCookies.map((cookie) => cookie.split(';')[0]).join('; ');

const oidcFormLogin = (): Plugin => ({
	name: 'oidc-form-login',
	configureServer(server) {
		server.middlewares.use('/login', async (req, res, next) => {
			if (req.headers['sec-fetch-mode'] === 'navigate') {
				next();
				return;
			}
			if (req.method === 'GET') {
				res.statusCode = 200;
				res.end();
				return;
			}
			if (req.method !== 'POST') {
				next();
				return;
			}
			const form = new URLSearchParams(await readBody(req));
			const username = form.get('username') ?? '';
			if (username === '' || form.get('password') !== username) {
				res.statusCode = 401;
				res.end();
				return;
			}
			const start = await fetch(`${target}/oauth2/authorization/oidc`, {redirect: 'manual'});
			const startCookies = start.headers.getSetCookie();
			const authorizeUrl = start.headers.get('location') ?? '';
			const approve = await fetch(authorizeUrl, {
				method: 'POST',
				redirect: 'manual',
				headers: {'Content-Type': 'application/x-www-form-urlencoded'},
				body: new URLSearchParams({username, claims: ''}),
			});
			const callbackUrl = new URL(approve.headers.get('location') ?? '');
			const callback = await fetch(`${target}${callbackUrl.pathname}${callbackUrl.search}`, {
				redirect: 'manual',
				headers: {Cookie: cookieHeader(startCookies)},
			});
			const sessionCookies = callback.headers.getSetCookie();
			if (callback.status >= 400 || sessionCookies.length === 0) {
				res.statusCode = 401;
				res.end();
				return;
			}
			res.setHeader(
				'Set-Cookie',
				sessionCookies.map((cookie) => cookie.replace(/;\s*Domain=[^;]*/i, '')),
			);
			res.statusCode = 204;
			res.end();
		});
	},
});

export default defineConfig((env) =>
	mergeConfig(typeof base === 'function' ? base(env) : base, {
		plugins: [oidcFormLogin()],
		server: {
			port: 3002,
			open: false,
			proxy: {
				'/v2': target,
				'/admin/config.js': target,
				'/oauth2': target,
				'/sso-callback': target,
				'/login/oauth2': target,
				'/logout': {target},
				'/session/heartbeat': {target},
			},
		},
	}),
);
