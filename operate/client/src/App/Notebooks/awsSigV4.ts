/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Minimal hand-rolled AWS SigV4 signer for a single POST request.
 * Uses the Web Crypto API (crypto.subtle) — no npm dependencies.
 *
 * Reference: https://docs.aws.amazon.com/general/latest/gr/sigv4_signing.html
 */

// ---------------------------------------------------------------------------
// Low-level crypto helpers
// ---------------------------------------------------------------------------

async function sha256Hex(message: string): Promise<string> {
  const msgBuffer = new TextEncoder().encode(message);
  const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
  return toHex(new Uint8Array(hashBuffer));
}

async function hmacSha256(key: Uint8Array, data: string): Promise<Uint8Array> {
  // TypeScript's strict DOM lib types `importKey`'s 2nd arg as `ArrayBuffer`
  // (via BufferSource), but at runtime a Uint8Array is always a valid typed
  // array key material.  The cast silences the compile error without any
  // runtime conversion that could confuse jsdom's SubtleCrypto.
  const cryptoKey = await crypto.subtle.importKey(
    'raw',
    key as unknown as ArrayBuffer,
    {name: 'HMAC', hash: 'SHA-256'},
    false,
    ['sign'],
  );
  const signature = await crypto.subtle.sign(
    'HMAC',
    cryptoKey,
    new TextEncoder().encode(data),
  );
  return new Uint8Array(signature);
}

function toHex(bytes: Uint8Array): string {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

// ---------------------------------------------------------------------------
// Signing-key derivation
// ---------------------------------------------------------------------------

async function deriveSigningKey(
  secretAccessKey: string,
  dateStamp: string, // YYYYMMDD
  region: string,
  service: string,
): Promise<Uint8Array> {
  const kSecret = new TextEncoder().encode(`AWS4${secretAccessKey}`);
  const kDate = await hmacSha256(kSecret, dateStamp);
  const kRegion = await hmacSha256(kDate, region);
  const kService = await hmacSha256(kRegion, service);
  const kSigning = await hmacSha256(kService, 'aws4_request');
  return kSigning;
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

export type SigV4Credentials = {
  accessKeyId: string;
  secretAccessKey: string;
  region: string;
  service: string;
};

/**
 * Compute the Authorization header and companion headers for a POST request.
 * Returns the full set of headers that must be sent with the request.
 */
export async function signRequest(
  credentials: SigV4Credentials,
  host: string,
  path: string,
  body: string,
): Promise<Record<string, string>> {
  const {accessKeyId, secretAccessKey, region, service} = credentials;

  // Timestamps
  const now = new Date();
  const amzDate = now.toISOString().replace(/[:-]/g, '').slice(0, 15) + 'Z'; // YYYYMMDDTHHmmssZ
  const dateStamp = amzDate.slice(0, 8); // YYYYMMDD

  // Payload hash
  const payloadHash = await sha256Hex(body);

  // Canonical headers (must be sorted, lowercase)
  const canonicalHeaders =
    `content-type:application/json\n` +
    `host:${host}\n` +
    `x-amz-content-sha256:${payloadHash}\n` +
    `x-amz-date:${amzDate}\n`;

  const signedHeaders = 'content-type;host;x-amz-content-sha256;x-amz-date';

  // Canonical request
  //
  // SigV4 quirk: for non-S3 services (Bedrock included), the URI in the
  // canonical request must be **doubly URI-encoded**. The actual HTTP request
  // path is singly encoded (e.g. "/model/arn%3A...%2F.../invoke"), but for
  // signing we re-encode each path segment so "%3A" becomes "%253A".
  // We split on "/" so the slashes between segments stay literal.
  const canonicalPath = path
    .split('/')
    .map((segment) => encodeURIComponent(segment))
    .join('/');

  const canonicalRequest = [
    'POST',
    canonicalPath,
    '', // query string (empty)
    canonicalHeaders,
    signedHeaders,
    payloadHash,
  ].join('\n');

  // Credential scope
  const credentialScope = `${dateStamp}/${region}/${service}/aws4_request`;

  // String to sign
  const hashedCanonicalRequest = await sha256Hex(canonicalRequest);
  const stringToSign = [
    'AWS4-HMAC-SHA256',
    amzDate,
    credentialScope,
    hashedCanonicalRequest,
  ].join('\n');

  // Signature
  const signingKey = await deriveSigningKey(
    secretAccessKey,
    dateStamp,
    region,
    service,
  );
  const signatureBytes = await hmacSha256(signingKey, stringToSign);
  const signature = toHex(signatureBytes);

  // Authorization header
  const authorization =
    `AWS4-HMAC-SHA256 Credential=${accessKeyId}/${credentialScope},` +
    `SignedHeaders=${signedHeaders},` +
    `Signature=${signature}`;

  return {
    'content-type': 'application/json',
    host,
    'x-amz-content-sha256': payloadHash,
    'x-amz-date': amzDate,
    Authorization: authorization,
  };
}
