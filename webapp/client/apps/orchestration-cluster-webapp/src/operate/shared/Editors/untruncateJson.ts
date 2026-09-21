/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type ContextType =
	| 'topLevel'
	| 'string'
	| 'stringEscaped'
	| 'stringUnicode'
	| 'number'
	| 'numberNeedsDigit'
	| 'numberNeedsExponent'
	| 'true'
	| 'false'
	| 'null'
	| 'arrayNeedsValue'
	| 'arrayNeedsComma'
	| 'objectNeedsKey'
	| 'objectNeedsColon'
	| 'objectNeedsValue'
	| 'objectNeedsComma';
type RespawnReason = 'stringEscape' | 'collectionItem';

function untruncateJson(json: string): {completed: string; collectionDepth: number} {
	const contextStack: ContextType[] = ['topLevel'];
	let position = 0;
	let respawnPosition: number | undefined;
	let respawnStackLength: number | undefined;
	let respawnReason: RespawnReason | undefined;
	const push = (context: ContextType) => contextStack.push(context);
	const replace = (context: ContextType) => (contextStack[contextStack.length - 1] = context);
	const pop = () => contextStack.pop();
	const setRespawn = (reason: RespawnReason) => {
		if (respawnPosition == null) {
			respawnPosition = position;
			respawnStackLength = contextStack.length;
			respawnReason = reason;
		}
	};
	const clearRespawn = (reason: RespawnReason) => {
		if (reason === respawnReason) {
			respawnPosition = undefined;
			respawnStackLength = undefined;
			respawnReason = undefined;
		}
	};
	const startAny = (char: string) => {
		if ('0' <= char && char <= '9') {
			push('number');
			return;
		}
		switch (char) {
			case '"':
				push('string');
				break;
			case '-':
				push('numberNeedsDigit');
				break;
			case 't':
				push('true');
				break;
			case 'f':
				push('false');
				break;
			case 'n':
				push('null');
				break;
			case '[':
				push('arrayNeedsValue');
				break;
			case '{':
				push('objectNeedsKey');
				break;
		}
	};

	for (; position < json.length; position++) {
		const char = json[position];
		if (char === undefined) {
			break;
		}
		switch (contextStack[contextStack.length - 1]) {
			case 'topLevel':
				startAny(char);
				break;
			case 'string':
				if (char === '"') {
					pop();
				} else if (char === '\\') {
					setRespawn('stringEscape');
					push('stringEscaped');
				}
				break;
			case 'stringEscaped':
				if (char === 'u') {
					push('stringUnicode');
				} else {
					clearRespawn('stringEscape');
					pop();
				}
				break;
			case 'stringUnicode':
				if (position - json.lastIndexOf('u', position) === 4) {
					clearRespawn('stringEscape');
					pop();
				}
				break;
			case 'number':
				if (char === '.') {
					replace('numberNeedsDigit');
				} else if (char === 'e' || char === 'E') {
					replace('numberNeedsExponent');
				} else if (char < '0' || char > '9') {
					position--;
					pop();
				}
				break;
			case 'numberNeedsDigit':
				replace('number');
				break;
			case 'numberNeedsExponent':
				replace(char === '+' || char === '-' ? 'numberNeedsDigit' : 'number');
				break;
			case 'true':
			case 'false':
			case 'null':
				if (char < 'a' || char > 'z') {
					position--;
					pop();
				}
				break;
			case 'arrayNeedsValue':
				if (char === ']') {
					pop();
				} else if (!'\u0020\u000D\u000A\u0009'.includes(char)) {
					clearRespawn('collectionItem');
					replace('arrayNeedsComma');
					startAny(char);
				}
				break;
			case 'arrayNeedsComma':
				if (char === ']') {
					pop();
				} else if (char === ',') {
					setRespawn('collectionItem');
					replace('arrayNeedsValue');
				}
				break;
			case 'objectNeedsKey':
				if (char === '}') {
					pop();
				} else if (char === '"') {
					setRespawn('collectionItem');
					replace('objectNeedsColon');
					push('string');
				}
				break;
			case 'objectNeedsColon':
				if (char === ':') {
					replace('objectNeedsValue');
				}
				break;
			case 'objectNeedsValue':
				if (!'\u0020\u000D\u000A\u0009'.includes(char)) {
					clearRespawn('collectionItem');
					replace('objectNeedsComma');
					startAny(char);
				}
				break;
			case 'objectNeedsComma':
				if (char === '}') {
					pop();
				} else if (char === ',') {
					setRespawn('collectionItem');
					replace('objectNeedsKey');
				}
				break;
		}
	}

	if (respawnStackLength != null) {
		contextStack.length = respawnStackLength;
	}
	const result = [respawnPosition != null ? json.slice(0, respawnPosition) : json];
	let collectionDepth = 0;
	for (let index = contextStack.length - 1; index >= 0; index--) {
		const context = contextStack[index];
		switch (context) {
			case 'string':
				result.push('"');
				break;
			case 'numberNeedsDigit':
			case 'numberNeedsExponent':
				result.push('0');
				break;
			case 'true':
			case 'false':
			case 'null': {
				const word = context;
				result.push(word.slice(json.length - json.lastIndexOf(word.charAt(0))));
				break;
			}
			case 'arrayNeedsValue':
			case 'arrayNeedsComma':
				result.push(']');
				collectionDepth++;
				break;
			case 'objectNeedsKey':
			case 'objectNeedsColon':
			case 'objectNeedsValue':
			case 'objectNeedsComma':
				result.push('}');
				collectionDepth++;
				break;
		}
	}
	return {completed: result.join(''), collectionDepth};
}

export {untruncateJson};
