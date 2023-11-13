/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.client.transport.aws;

import jakarta.json.JsonObject;
import jakarta.json.stream.JsonParser;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.JsonEndpoint;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.TransportException;
import org.opensearch.client.transport.TransportOptions;
import org.opensearch.client.transport.endpoints.BooleanEndpoint;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.opensearch.client.util.OpenSearchRequestBodyBuffer;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.SdkAutoCloseable;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.zip.GZIPInputStream;


/**
 * Implementation of the OpenSearchTransport interface that sends signed requests using
 * the AWS v2 SDK HTTP clients, to connect to an AWS OpenSearch service using IAM authentication.
 */
public class AwsSdk2Transport implements OpenSearchTransport {
    /**
     * By default, requests that exceed this size will be automatically compressed.
     * {@link AwsSdk2TransportOptions} can be used to override this setting or disable compression.
     */
    public static final Integer DEFAULT_REQUEST_COMPRESSION_SIZE = 8192;

    private static final byte[] NO_BYTES = new byte[0];
    private final SdkAutoCloseable httpClient;
    private final String host;
    private final String signingServiceName;
    private final Region signingRegion;
    private final JsonpMapper defaultMapper;
    private final AwsSdk2TransportOptions transportOptions;

    /**
     * Create an {@link OpenSearchTransport} with an asynchronous AWS HTTP client.
     * <p>
     * Note that asynchronous OpenSearch requests sent through this transport will be dispatched
     * *synchronously* on the calling thread.
     *
     * @param asyncHttpClient Asynchronous HTTP client to use for OpenSearch requests.
     * @param host The fully qualified domain name to connect to.
     * @param signingRegion The AWS region for which requests will be signed. This should typically match the region in `host`.
     * @param options Options that apply to all requests. Can be null. Create with
     *                {@link AwsSdk2TransportOptions#builder()} and use these to specify non-default credentials,
     *                compression options, etc.
     */
    public AwsSdk2Transport(
            @CheckForNull SdkAsyncHttpClient asyncHttpClient,
            @Nonnull String host,
            @Nonnull Region signingRegion,
            @CheckForNull AwsSdk2TransportOptions options) {
        this(asyncHttpClient, host, "es", signingRegion, options);
    }

    /**
     * Create an {@link OpenSearchTransport} with a synchronous AWS HTTP client.
     *
     * @param syncHttpClient Synchronous HTTP client to use for OpenSearch requests.
     * @param host The fully qualified domain name to connect to.
     * @param signingRegion The AWS region for which requests will be signed. This should typically match the region in `host`.
     * @param options Options that apply to all requests. Can be null. Create with
     *                {@link AwsSdk2TransportOptions#builder()} and use these to specify non-default credentials,
     *                compression options, etc.
     */
    public AwsSdk2Transport(
            @CheckForNull SdkHttpClient syncHttpClient,
            @Nonnull String host,
            @Nonnull Region signingRegion,
            @CheckForNull AwsSdk2TransportOptions options) {
        this(syncHttpClient, host, "es", signingRegion, options);
    }

    /**
     * Create an {@link OpenSearchTransport} with an asynchronous AWS HTTP client.
     * <p>
     * Note that asynchronous OpenSearch requests sent through this transport will be dispatched
     * *synchronously* on the calling thread.
     *
     * @param asyncHttpClient Asynchronous HTTP client to use for OpenSearch requests.
     * @param host The fully qualified domain name to connect to.
     * @param signingRegion The AWS region for which requests will be signed. This should typically match the region in `host`.
     * @param signingServiceName The AWS signing service name, one of `es` (Amazon OpenSearch) or `aoss` (Amazon OpenSearch Serverless).
     * @param options Options that apply to all requests. Can be null. Create with
     *                {@link AwsSdk2TransportOptions#builder()} and use these to specify non-default credentials,
     *                compression options, etc.
     */
    public AwsSdk2Transport(
            @CheckForNull SdkAsyncHttpClient asyncHttpClient,
            @Nonnull String host,
            @Nonnull String signingServiceName,
            @Nonnull Region signingRegion,
            @CheckForNull AwsSdk2TransportOptions options) {
        this((SdkAutoCloseable) asyncHttpClient, host, signingServiceName, signingRegion, options);
    }

    /**
     * Create an {@link OpenSearchTransport} with a synchronous AWS HTTP client.
     *
     * @param syncHttpClient Synchronous HTTP client to use for OpenSearch requests.
     * @param host The fully qualified domain name to connect to.
     * @param signingRegion The AWS region for which requests will be signed. This should typically match the region in `host`.
     * @param signingServiceName The AWS signing service name, one of `es` (Amazon OpenSearch) or `aoss` (Amazon OpenSearch Serverless).
     * @param options Options that apply to all requests. Can be null. Create with
     *                {@link AwsSdk2TransportOptions#builder()} and use these to specify non-default credentials,
     *                compression options, etc.
     */
    public AwsSdk2Transport(
            @CheckForNull SdkHttpClient syncHttpClient,
            @Nonnull String host,
            @Nonnull String signingServiceName,
            @Nonnull Region signingRegion,
            @CheckForNull AwsSdk2TransportOptions options) {
        this((SdkAutoCloseable) syncHttpClient, host, signingServiceName, signingRegion, options);
    }

    private AwsSdk2Transport(
            @CheckForNull SdkAutoCloseable httpClient,
            @Nonnull String host,
            @Nonnull String signingServiceName,
            @Nonnull Region signingRegion,
            @CheckForNull AwsSdk2TransportOptions options) {
        Objects.requireNonNull(host, "Target OpenSearch service host must not be null");
        this.httpClient = httpClient;
        this.host = host;
        this.signingServiceName = signingServiceName;
        this.signingRegion = signingRegion;
        this.transportOptions = options != null ? options : AwsSdk2TransportOptions.builder().build();
        this.defaultMapper = Optional.ofNullable(options)
                .map(AwsSdk2TransportOptions::mapper)
                .orElse(new JacksonJsonpMapper());
    }

    @Override
    public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
            RequestT request,
            Endpoint<RequestT, ResponseT, ErrorT> endpoint,
            @Nullable TransportOptions options
    ) throws IOException {

        OpenSearchRequestBodyBuffer requestBody = prepareRequestBody(request, endpoint, options);
        SdkHttpFullRequest clientReq = prepareRequest(request, endpoint, options, requestBody);

        if (httpClient instanceof SdkHttpClient) {
            return executeSync((SdkHttpClient) httpClient, clientReq, endpoint, options);
        } else if (httpClient instanceof SdkAsyncHttpClient) {
            try {
                return executeAsync((SdkAsyncHttpClient) httpClient, clientReq, requestBody, endpoint, options).get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause != null) {
                    if (cause instanceof IOException) {
                        throw (IOException) cause;
                    }
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    }
                    throw new RuntimeException(cause);
                }
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new IOException("HttpRequest was interrupted", e);
            }
        } else {
            throw new IOException("invalid httpClient: " + httpClient);
        }
    }

    @Override
    public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(
            RequestT request,
            Endpoint<RequestT, ResponseT, ErrorT> endpoint,
            @Nullable TransportOptions options
    ) {
        try {
            OpenSearchRequestBodyBuffer requestBody = prepareRequestBody(request, endpoint, options);
            SdkHttpFullRequest clientReq = prepareRequest(request, endpoint, options, requestBody);
            if (httpClient instanceof SdkAsyncHttpClient) {
                return executeAsync((SdkAsyncHttpClient) httpClient, clientReq, requestBody, endpoint, options);
            } else if (httpClient instanceof SdkHttpClient) {
                ResponseT result = executeSync((SdkHttpClient) httpClient, clientReq, endpoint, options);
                return CompletableFuture.completedFuture(result);
            } else {
                throw new IOException("invalid httpClient: " + httpClient);
            }
        } catch (Throwable e) {
            CompletableFuture<ResponseT> cf = new CompletableFuture<>();
            cf.completeExceptionally(e);
            return cf;
        }
    }

    @Override
    public JsonpMapper jsonpMapper() {
        return defaultMapper;
    }

    @Override
    public AwsSdk2TransportOptions options() {
        return transportOptions;
    }

    @Override
    public void close() {
    }

    @CheckForNull
    private <RequestT> OpenSearchRequestBodyBuffer prepareRequestBody(
            RequestT request,
            Endpoint<RequestT, ?, ?> endpoint,
            TransportOptions options
    ) throws IOException {
        if (endpoint.hasRequestBody()) {
            final JsonpMapper mapper = Optional.ofNullable(options)
                    .map(o -> o instanceof AwsSdk2TransportOptions ? ((AwsSdk2TransportOptions) o) : null)
                    .map(AwsSdk2TransportOptions::mapper)
                    .orElse(defaultMapper);
            final int maxUncompressedSize = Optional.ofNullable(options)
                    .map(o -> o instanceof AwsSdk2TransportOptions ? ((AwsSdk2TransportOptions) o) : null)
                    .map(AwsSdk2TransportOptions::requestCompressionSize)
                    .or(()->Optional.ofNullable(transportOptions.requestCompressionSize()))
                    .orElse(DEFAULT_REQUEST_COMPRESSION_SIZE);

            OpenSearchRequestBodyBuffer buffer = new OpenSearchRequestBodyBuffer(mapper, maxUncompressedSize);
            buffer.addContent(request);
            buffer.close();
            return buffer;
        }
        return null;
    }

    private <RequestT> SdkHttpFullRequest prepareRequest(
            RequestT request,
            Endpoint<RequestT, ?, ?> endpoint,
            @CheckForNull TransportOptions options,
            @CheckForNull OpenSearchRequestBodyBuffer body
    ) {
        SdkHttpFullRequest.Builder req = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.fromValue(endpoint.method(request)));

        StringBuilder url = new StringBuilder();
        url.append("https://").append(host);
        String path = endpoint.requestUrl(request);
        if (!path.startsWith("/")) {
            url.append('/');
        }
        url.append(path);
        Map<String, String> params = endpoint.queryParameters(request);
        if (params != null && !params.isEmpty()) {
            char sep = '?';
            for (var ent : params.entrySet()) {
                url.append(sep).append(ent.getKey()).append('=');
                url.append(URLEncoder.encode(ent.getValue(), StandardCharsets.UTF_8));
                sep = '&';
            }
        }
        applyOptionsParams(url, transportOptions);
        applyOptionsParams(url, options);
        try {
            req.uri(new URI(url.toString()));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid request URI: " + url.toString());
        }
        applyOptionsHeaders(req, transportOptions);
        applyOptionsHeaders(req, options);
        if (endpoint.hasRequestBody() && body != null) {
            req.putHeader("Content-Type", body.getContentType());
            String encoding = body.getContentEncoding();
            if (encoding != null) {
                req.putHeader("Content-Encoding", encoding);
            }
            req.putHeader("Content-Length", String.valueOf(body.getContentLength()));
            req.contentStreamProvider(body::getInputStream);
            // To add the "X-Amz-Content-Sha256" header, it needs to set as required.
            // It is a required header for Amazon OpenSearch Serverless.
            req.putHeader("x-amz-content-sha256", "required");
        }

        boolean responseCompression = Optional.ofNullable(options)
                .map(o -> o instanceof AwsSdk2TransportOptions ? ((AwsSdk2TransportOptions) o) : null)
                .map(AwsSdk2TransportOptions::responseCompression)
                .or(() -> Optional.ofNullable(transportOptions.responseCompression()))
                .orElse(Boolean.TRUE);
        if (responseCompression) {
            req.putHeader("Accept-Encoding", "gzip");
        } else {
            req.removeHeader("Accept-Encoding");
        }

        final AwsCredentialsProvider credentials = Optional.ofNullable(options)
                .map(o -> o instanceof AwsSdk2TransportOptions ? ((AwsSdk2TransportOptions) o) : null)
                .map(AwsSdk2TransportOptions::credentials)
                .or(() -> Optional.ofNullable(transportOptions.credentials()))
                .orElse(DefaultCredentialsProvider.create());

        Aws4SignerParams signerParams = Aws4SignerParams.builder()
                .awsCredentials(credentials.resolveCredentials())
                .signingName(this.signingServiceName)
                .signingRegion(signingRegion)
                .build();
        return Aws4Signer.create().sign(req.build(), signerParams);
    }

    private void applyOptionsParams(StringBuilder url, TransportOptions options) {
        if (options == null) {
            return;
        }
        Map<String, String> params = options.queryParameters();
        if (params != null && !params.isEmpty()) {
            char sep = url.indexOf("?") < 0 ? '?' : '&';
            for (Map.Entry<String, String> param : params.entrySet()) {
                url.append(sep).append(param.getKey()).append('=');
                url.append(URLEncoder.encode(param.getValue(), StandardCharsets.UTF_8));
                sep = '?';
            }
        }
    }

    private void applyOptionsHeaders(SdkHttpFullRequest.Builder builder, TransportOptions options) {
        if (options == null) {
            return;
        }
        Collection<Map.Entry<String, String>> headers = options.headers();
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> header : headers) {
                builder.appendHeader(header.getKey(), header.getValue());
            }
        }
    }

    private <ResponseT> ResponseT executeSync(
            SdkHttpClient syncHttpClient,
            SdkHttpFullRequest httpRequest,
            Endpoint<?, ResponseT, ?> endpoint,
            TransportOptions options
    ) throws IOException {

        HttpExecuteRequest.Builder executeRequest = HttpExecuteRequest.builder().request(httpRequest);
        if (httpRequest.contentStreamProvider().isPresent()) {
            executeRequest.contentStreamProvider(httpRequest.contentStreamProvider().get());
        }
        HttpExecuteResponse executeResponse = syncHttpClient.prepareRequest(executeRequest.build()).call();
        AbortableInputStream bodyStream = null;
        try {
            bodyStream = executeResponse.responseBody().orElse(null);
            SdkHttpResponse httpResponse = executeResponse.httpResponse();
            return parseResponse(httpResponse, bodyStream, endpoint, options);
        } finally {
            if (bodyStream != null) {
                bodyStream.close();
            }
        }
    }

    private <ResponseT> CompletableFuture<ResponseT> executeAsync(
            SdkAsyncHttpClient asyncHttpClient,
            SdkHttpFullRequest httpRequest,
            @CheckForNull OpenSearchRequestBodyBuffer requestBody,
            Endpoint<?, ResponseT, ?> endpoint,
            TransportOptions options
    ) {
        byte[] requestBodyArray = requestBody == null ? NO_BYTES : requestBody.getByteArray();

        final AsyncCapturingResponseHandler responseHandler = new AsyncCapturingResponseHandler();
        AsyncExecuteRequest.Builder executeRequest = AsyncExecuteRequest.builder()
                .request(httpRequest)
                .requestContentPublisher(new AsyncByteArrayContentPublisher(requestBodyArray))
                .responseHandler(responseHandler);
        CompletableFuture<Void> executeFuture = asyncHttpClient.execute(executeRequest.build());
        return executeFuture
                .thenCompose(_v -> responseHandler.getHeaderPromise())
                .thenCompose(response -> responseHandler.getBodyPromise().thenCompose(responseBody -> {
                    CompletableFuture<ResponseT> ret = new CompletableFuture<>();
                    try {
                        InputStream bodyStream = new ByteArrayInputStream(responseBody);
                        ret.complete(parseResponse(response, bodyStream, endpoint, options));
                    } catch (Throwable e) {
                        ret.completeExceptionally(e);
                    }
                    return ret;
                }));
    }

    private <ResponseT, ErrorT> ResponseT parseResponse(
            @Nonnull SdkHttpResponse httpResponse,
            @CheckForNull InputStream bodyStream,
            @Nonnull Endpoint<?, ResponseT, ErrorT> endpoint,
            @CheckForNull TransportOptions options
    ) throws IOException {
        final JsonpMapper mapper = Optional.ofNullable(options)
                .map(o -> o instanceof AwsSdk2TransportOptions ? ((AwsSdk2TransportOptions) o) : null)
                .map(AwsSdk2TransportOptions::mapper)
                .orElse(defaultMapper);

        int statusCode = httpResponse.statusCode();
        boolean isZipped = httpResponse.firstMatchingHeader("Content-Encoding")
                .map(enc -> enc.contains("gzip"))
                .orElse(Boolean.FALSE);
        if (bodyStream != null && isZipped) {
            bodyStream = new GZIPInputStream(bodyStream);
        }

        if (statusCode == 403) {
            // Authentication errors from AWS do not follow OpenSearch exception format
            ErrorCause.Builder cause = new ErrorCause.Builder();
            cause.type("security_exception");
            cause.reason("authentication/authorization failure");

            if (bodyStream != null) {
                try (JsonParser parser = mapper.jsonProvider().createParser(bodyStream)) {
                    JsonObject val = JsonpDeserializer.jsonValueDeserializer()
                            .deserialize(parser, mapper)
                            .asJsonObject();
                    String message = null;
                    if (val.get("error") instanceof JsonObject) {
                        message = val.get("error").asJsonObject().getString("reason", null);
                    }
                    if (message == null) {
                        message = val.getString("Message", null);
                    }
                    if (message == null) {
                        message = val.getString("message", null);
                    }
                    if (message != null) {
                        cause.reason(message);
                    }
                } catch (Exception e) {
                    // OK.  We'll use default message
                }
            }

            ErrorResponse error = ErrorResponse.of(err -> err.status(statusCode).error(cause.build()));
            throw new OpenSearchException(error);
        }

        if (endpoint.isError(statusCode)) {
            JsonpDeserializer<ErrorT> errorDeserializer = endpoint.errorDeserializer(statusCode);
            if (errorDeserializer == null || bodyStream == null) {
                throw new TransportException(
                        "Request failed with status code '" + statusCode + "'"
                );
            }
            try {
                try (JsonParser parser = mapper.jsonProvider().createParser(bodyStream)) {
                    ErrorT error = errorDeserializer.deserialize(parser, mapper);
                    throw new OpenSearchException((ErrorResponse) error);
                }
            } catch (OpenSearchException e) {
                throw e;
            } catch (Exception e) {
                // can't parse the error - use a general exception
                ErrorCause.Builder cause = new ErrorCause.Builder();
                cause.type("http_exception");
                cause.reason("server returned " + statusCode);
                ErrorResponse error = ErrorResponse.of(err -> err.status(statusCode).error(cause.build()));
                throw new OpenSearchException(error);
            }
        } else {
            if (endpoint instanceof BooleanEndpoint) {
                BooleanEndpoint<?> bep = (BooleanEndpoint<?>) endpoint;
                @SuppressWarnings("unchecked")
                ResponseT response = (ResponseT) new BooleanResponse(bep.getResult(statusCode));
                return response;
            } else if (endpoint instanceof JsonEndpoint) {
                JsonEndpoint<?, ResponseT, ?> jsonEndpoint = (JsonEndpoint<?, ResponseT, ?>) endpoint;
                // Successful response
                ResponseT response = null;
                JsonpDeserializer<ResponseT> responseParser = jsonEndpoint.responseDeserializer();
                if (responseParser != null) {
                    // Expecting a body
                    if (bodyStream == null) {
                        throw new TransportException("Expecting a response body, but none was sent");
                    }
                    try (JsonParser parser = mapper.jsonProvider().createParser(bodyStream)) {
                        try {
                            response = responseParser.deserialize(parser, mapper);
                        } catch (NullPointerException e) {
                            response = responseParser.deserialize(parser, mapper);
                        }
                    }
                    ;
                }
                return response;
            } else {
                throw new TransportException("Unhandled endpoint type: '" + endpoint.getClass().getName() + "'");
            }
        }
    }
}
