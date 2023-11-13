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

package org.opensearch.client.transport.httpclient5;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nullable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.entity.GzipDecompressingEntity;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.io.entity.BufferedHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Args;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.NdJsonpSerializable;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.JsonEndpoint;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.TransportException;
import org.opensearch.client.transport.TransportOptions;
import org.opensearch.client.transport.endpoints.BooleanEndpoint;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.opensearch.client.transport.httpclient5.internal.HttpUriRequestProducer;
import org.opensearch.client.transport.httpclient5.internal.Node;
import org.opensearch.client.transport.httpclient5.internal.NodeSelector;
import org.opensearch.client.util.MissingRequiredPropertyException;

import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

public class ApacheHttpClient5Transport implements OpenSearchTransport {
    private static final Log logger = LogFactory.getLog(ApacheHttpClient5Transport.class);
    static final ContentType JsonContentType = ContentType.APPLICATION_JSON;

    private final JsonpMapper mapper;
    private final CloseableHttpAsyncClient client;
    private final ApacheHttpClient5Options transportOptions;
    private final ConcurrentMap<HttpHost, DeadHostState> denylist = new ConcurrentHashMap<>();
    private final AtomicInteger lastNodeIndex = new AtomicInteger(0);
    private volatile NodeTuple<List<Node>> nodeTuple;
    private final NodeSelector nodeSelector;
    private final WarningsHandler warningsHandler;
    private final FailureListener failureListener;
    private final boolean compressionEnabled;
    private final boolean chunkedEnabled;
    private final String pathPrefix;
    private final List<Header> defaultHeaders;

    public ApacheHttpClient5Transport(final CloseableHttpAsyncClient client, final Header[] defaultHeaders,
            final List<Node> nodes, final JsonpMapper mapper, @Nullable TransportOptions options, final String pathPrefix, 
                final FailureListener failureListener, final NodeSelector nodeSelector, final boolean strictDeprecationMode, 
                    final boolean compressionEnabled, final boolean chunkedEnabled) {
        this.mapper = mapper;
        this.client = client;
        this.defaultHeaders = Collections.unmodifiableList(Arrays.asList(defaultHeaders));
        this.pathPrefix = pathPrefix;
        this.transportOptions = (options == null) ? ApacheHttpClient5Options.initialOptions() : ApacheHttpClient5Options.of(options);
        this.warningsHandler = strictDeprecationMode ? WarningsHandler.STRICT : WarningsHandler.PERMISSIVE;
        this.nodeSelector = (nodeSelector == null) ? NodeSelector.ANY : nodeSelector;
        this.failureListener = (failureListener == null) ? new FailureListener() : failureListener;
        this.chunkedEnabled = chunkedEnabled;
        this.compressionEnabled = compressionEnabled;
        setNodes(nodes);
    }

    @Override
    public <RequestT, ResponseT, ErrorT> ResponseT performRequest(RequestT request,
            Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) throws IOException {
        try {
            return performRequestAsync(request, endpoint, options).join();
        } catch (final CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            } else if (ex.getCause() instanceof IOException) {
                throw (IOException) ex.getCause();
            } else {
                throw new IOException(ex.getCause());
            } 
        }
    }

    @Override
    public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(RequestT request,
            Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) {

        final ApacheHttpClient5Options requestOptions = (options == null) ? transportOptions : ApacheHttpClient5Options.of(options);
        final CompletableFuture<Response> future = new CompletableFuture<>();
        final HttpUriRequestBase clientReq = prepareLowLevelRequest(request, endpoint, requestOptions);
        final WarningsHandler warningsHandler = (requestOptions.getWarningsHandler() == null) ? 
            this.warningsHandler : requestOptions.getWarningsHandler();

        try {
            performRequestAsync(nextNodes(), requestOptions, clientReq, warningsHandler, future);
        } catch(final IOException ex) {
            future.completeExceptionally(ex);
        }
        
        return future.thenApply(r -> {
            try {
                return (ResponseT)prepareResponse(r, endpoint);
            } catch (final IOException ex) {
                throw new CompletionException(ex);
            }
        });
    }

    @Override
    public JsonpMapper jsonpMapper() {
        return mapper;
    }

    @Override
    public TransportOptions options() {
        return transportOptions;
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    private void performRequestAsync(final NodeTuple<Iterator<Node>> nodeTuple, final ApacheHttpClient5Options options,
            final HttpUriRequestBase request, final WarningsHandler warningsHandler, final CompletableFuture<Response> listener) {
        final RequestContext context = createContextForNextAttempt(options, request, nodeTuple.nodes.next(), nodeTuple.authCache);
        Future<ClassicHttpResponse> future = client.execute(
            context.requestProducer,
            context.asyncResponseConsumer,
            context.context,
            new FutureCallback<ClassicHttpResponse>() {
                @Override
                public void completed(ClassicHttpResponse httpResponse) {
                    try {
                        ResponseOrResponseException responseOrResponseException = convertResponse(request, context.node,
                            httpResponse, warningsHandler);
                        if (responseOrResponseException.responseException == null) {
                            listener.complete(responseOrResponseException.response);
                        } else {
                            if (nodeTuple.nodes.hasNext()) {
                                performRequestAsync(nodeTuple, options, request, warningsHandler, listener);
                            } else {
                                listener.completeExceptionally(responseOrResponseException.responseException);
                            }
                        }
                    } catch (Exception e) {
                        listener.completeExceptionally(e);
                    }
                }

                @Override
                public void failed(Exception failure) {
                    try {
                        onFailure(context.node);
                        if (nodeTuple.nodes.hasNext()) {
                            performRequestAsync(nodeTuple, options, request, warningsHandler, listener);
                        } else {
                            listener.completeExceptionally(failure);
                        }
                    } catch (Exception e) {
                        listener.completeExceptionally(e);
                    }
                }

                @Override
                public void cancelled() {
                    listener.completeExceptionally(new CancellationException("request was cancelled"));
                }
            }
        );

        if (future instanceof org.apache.hc.core5.concurrent.Cancellable) {
            request.setDependency((org.apache.hc.core5.concurrent.Cancellable) future);
        }
    }
    
    /**
     * Replaces the nodes with which the client communicates.
     *
     * @param nodes the new nodes to communicate with.
     */
    private void setNodes(Collection<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("nodes must not be null or empty");
        }
        AuthCache authCache = new BasicAuthCache();

        Map<HttpHost, Node> nodesByHost = new LinkedHashMap<>();
        for (Node node : nodes) {
            Objects.requireNonNull(node, "node cannot be null");
            // TODO should we throw an IAE if we have two nodes with the same host?
            nodesByHost.put(node.getHost(), node);
            authCache.put(node.getHost(), new BasicScheme());
        }
        this.nodeTuple = new NodeTuple<>(Collections.unmodifiableList(new ArrayList<>(nodesByHost.values())), authCache);
        this.denylist.clear();
    }

    private ResponseOrResponseException convertResponse(final HttpUriRequestBase request, final Node node, 
            final ClassicHttpResponse httpResponse, final WarningsHandler warningsHandler) throws IOException {
        int statusCode = httpResponse.getCode();

        Optional.ofNullable(httpResponse.getEntity())
            .map(HttpEntity::getContentEncoding)
            .filter("gzip"::equalsIgnoreCase)
            .map(gzipHeaderValue -> new GzipDecompressingEntity(httpResponse.getEntity()))
            .ifPresent(httpResponse::setEntity);

        Response response = new Response(new RequestLine(request), node.getHost(), httpResponse);
        Set<Integer> ignoreErrorCodes = getIgnoreErrorCodes("400,401,403,404,405", request.getMethod());
        if (isSuccessfulResponse(statusCode) || ignoreErrorCodes.contains(response.getStatusLine().getStatusCode())) {
            onResponse(node);
            if (warningsHandler.warningsShouldFailRequest(response.getWarnings())) {
                throw new WarningFailureException(response);
            }
            return new ResponseOrResponseException(response);
        }
        ResponseException responseException = new ResponseException(response);
        if (isRetryStatus(statusCode)) {
            // mark host dead and retry against next one
            onFailure(node);
            return new ResponseOrResponseException(responseException);
        }
        // mark host alive and don't retry, as the error should be a request problem
        onResponse(node);
        throw responseException;
    }

    private static Set<Integer> getIgnoreErrorCodes(String ignoreString, String requestMethod) {
        Set<Integer> ignoreErrorCodes;
        if (ignoreString == null) {
            if (HttpHead.METHOD_NAME.equals(requestMethod)) {
                // 404 never causes error if returned for a HEAD request
                ignoreErrorCodes = Collections.singleton(404);
            } else {
                ignoreErrorCodes = Collections.emptySet();
            }
        } else {
            String[] ignoresArray = ignoreString.split(",");
            ignoreErrorCodes = new HashSet<>();
            if (HttpHead.METHOD_NAME.equals(requestMethod)) {
                // 404 never causes error if returned for a HEAD request
                ignoreErrorCodes.add(404);
            }
            for (String ignoreCode : ignoresArray) {
                try {
                    ignoreErrorCodes.add(Integer.valueOf(ignoreCode));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("ignore value should be a number, found [" + ignoreString + "] instead", e);
                }
            }
        }
        return ignoreErrorCodes;
    }

    private static boolean isSuccessfulResponse(int statusCode) {
        return statusCode < 300;
    }

    private static boolean isRetryStatus(int statusCode) {
        switch (statusCode) {
            case 502:
            case 503:
            case 504:
                return true;
        }
        return false;
    }

    /**
     * Returns a non-empty {@link Iterator} of nodes to be used for a request
     * that match the {@link NodeSelector}.
     * <p>
     * If there are no living nodes that match the {@link NodeSelector}
     * this will return the dead node that matches the {@link NodeSelector}
     * that is closest to being revived.
     * @throws IOException if no nodes are available
     */
    private NodeTuple<Iterator<Node>> nextNodes() throws IOException {
        NodeTuple<List<Node>> nodeTuple = this.nodeTuple;
        Iterable<Node> hosts = selectNodes(nodeTuple, denylist, lastNodeIndex, nodeSelector);
        return new NodeTuple<>(hosts.iterator(), nodeTuple.authCache);
    }

    /**
     * Select nodes to try and sorts them so that the first one will be tried initially, then the following ones
     * if the previous attempt failed and so on. Package private for testing.
     */
    static Iterable<Node> selectNodes(
        NodeTuple<List<Node>> nodeTuple,
        Map<HttpHost, DeadHostState> denylist,
        AtomicInteger lastNodeIndex,
        NodeSelector nodeSelector
    ) throws IOException {
        /*
         * Sort the nodes into living and dead lists.
         */
        List<Node> livingNodes = new ArrayList<>(Math.max(0, nodeTuple.nodes.size() - denylist.size()));
        List<DeadNode> deadNodes = new ArrayList<>(denylist.size());
        for (Node node : nodeTuple.nodes) {
            DeadHostState deadness = denylist.get(node.getHost());
            if (deadness == null || deadness.shallBeRetried()) {
                livingNodes.add(node);
            } else {
                deadNodes.add(new DeadNode(node, deadness));
            }
        }

        if (false == livingNodes.isEmpty()) {
            /*
             * Normal state: there is at least one living node. If the
             * selector is ok with any over the living nodes then use them
             * for the request.
             */
            List<Node> selectedLivingNodes = new ArrayList<>(livingNodes);
            nodeSelector.select(selectedLivingNodes);
            if (false == selectedLivingNodes.isEmpty()) {
                /*
                 * Rotate the list using a global counter as the distance so subsequent
                 * requests will try the nodes in a different order.
                 */
                Collections.rotate(selectedLivingNodes, lastNodeIndex.getAndIncrement());
                return selectedLivingNodes;
            }
        }

        /*
         * Last resort: there are no good nodes to use, either because
         * the selector rejected all the living nodes or because there aren't
         * any living ones. Either way, we want to revive a single dead node
         * that the NodeSelectors are OK with. We do this by passing the dead
         * nodes through the NodeSelector so it can have its say in which nodes
         * are ok. If the selector is ok with any of the nodes then we will take
         * the one in the list that has the lowest revival time and try it.
         */
        if (false == deadNodes.isEmpty()) {
            final List<DeadNode> selectedDeadNodes = new ArrayList<>(deadNodes);
            /*
             * We'd like NodeSelectors to remove items directly from deadNodes
             * so we can find the minimum after it is filtered without having
             * to compare many things. This saves us a sort on the unfiltered
             * list.
             */
            nodeSelector.select(() -> new DeadNodeIteratorAdapter(selectedDeadNodes.iterator()));
            if (false == selectedDeadNodes.isEmpty()) {
                return Collections.singletonList(Collections.min(selectedDeadNodes).node);
            }
        }
        throw new IOException(
            "NodeSelector [" + nodeSelector + "] rejected all nodes, " + "living " + livingNodes + " and dead " + deadNodes
        );
    }

    /**
     * Called after each failed attempt.
     * Receives as an argument the host that was used for the failed attempt.
     */
    private void onFailure(Node node) {
        while (true) {
            DeadHostState previousDeadHostState = denylist.putIfAbsent(
                node.getHost(),
                new DeadHostState(DeadHostState.DEFAULT_TIME_SUPPLIER)
            );
            if (previousDeadHostState == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("added [" + node + "] to denylist");
                }
                break;
            }
            if (denylist.replace(node.getHost(), previousDeadHostState, new DeadHostState(previousDeadHostState))) {
                if (logger.isDebugEnabled()) {
                    logger.debug("updated [" + node + "] already in denylist");
                }
                break;
            }
        }
        failureListener.onFailure(node);
    }

    private RequestContext createContextForNextAttempt(final ApacheHttpClient5Options options, 
            final HttpUriRequestBase request, final Node node, final AuthCache authCache) {
        request.reset();
        return new RequestContext(options, request, node, authCache);
    }

    private <ResponseT, ErrorT> ResponseT prepareResponse(Response clientResp,
            Endpoint<?, ResponseT, ErrorT> endpoint
    ) throws IOException {

        try {
            int statusCode = clientResp.getStatusLine().getStatusCode();

            if (endpoint.isError(statusCode)) {
                JsonpDeserializer<ErrorT> errorDeserializer = endpoint.errorDeserializer(statusCode);
                if (errorDeserializer == null) {
                    throw new TransportException(
                        "Request failed with status code '" + statusCode + "'",
                        new ResponseException(clientResp)
                    );
                }

                HttpEntity entity = clientResp.getEntity();
                if (entity == null) {
                    throw new TransportException(
                        "Expecting a response body, but none was sent",
                        new ResponseException(clientResp)
                    );
                }

                // We may have to replay it.
                entity = new BufferedHttpEntity(entity);

                try {
                    InputStream content = entity.getContent();
                    try (JsonParser parser = mapper.jsonProvider().createParser(content)) {
                        ErrorT error = errorDeserializer.deserialize(parser, mapper);
                        // TODO: have the endpoint provide the exception constructor
                        throw new OpenSearchException((ErrorResponse) error);
                    }
                } catch(MissingRequiredPropertyException errorEx) {
                    // Could not decode exception, try the response type
                    try {
                        ResponseT response = decodeResponse(statusCode, entity, clientResp, endpoint);
                        return response;
                    } catch(Exception respEx) {
                        // No better luck: throw the original error decoding exception
                        throw new TransportException("Failed to decode error response", new ResponseException(clientResp));
                    }
                }
            } else {
                return decodeResponse(statusCode, clientResp.getEntity(), clientResp, endpoint);
            }
        } finally {
            EntityUtils.consume(clientResp.getEntity());
        }
    }

    private <RequestT> HttpUriRequestBase prepareLowLevelRequest(
            RequestT request,
            Endpoint<RequestT, ?, ?> endpoint,
            @Nullable ApacheHttpClient5Options options
    ) {
        final String method = endpoint.method(request);
        final String path = endpoint.requestUrl(request);
        final Map<String, String> params = endpoint.queryParameters(request);

        final URI uri = buildUri(pathPrefix, path, params);
        final HttpUriRequestBase clientReq = new HttpUriRequestBase(method, uri);
        if (endpoint.hasRequestBody()) {
            // Request has a body and must implement JsonpSerializable or NdJsonpSerializable
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            if (request instanceof NdJsonpSerializable) {
                writeNdJson((NdJsonpSerializable) request, baos);
            } else {
                JsonGenerator generator = mapper.jsonProvider().createGenerator(baos);
                mapper.serialize(request, generator);
                generator.close();
            }

            addRequestBody(clientReq, new ByteArrayEntity(baos.toByteArray(), JsonContentType));
        }

        setHeaders(clientReq, options.headers());

        if (options.getRequestConfig() != null) {
            clientReq.setConfig(options.getRequestConfig());
        }

        return clientReq;
    }

    private HttpUriRequestBase addRequestBody(HttpUriRequestBase httpRequest, HttpEntity entity) {
        if (entity != null) {
            if (compressionEnabled) {
                if (chunkedEnabled) {
                    entity = new ContentCompressingEntity(entity, chunkedEnabled);
                } else {
                    entity = new ContentCompressingEntity(entity);
                }
            } else if (chunkedEnabled) {
                entity = new ContentHttpEntity(entity, chunkedEnabled);
            }
            httpRequest.setEntity(entity);
        }
        return httpRequest;
    }

    private void setHeaders(HttpRequest httpRequest, Collection<Entry<String, String>> requestHeaders) {
        // request headers override default headers, so we don't add default headers if they exist as request headers
        final Set<String> requestNames = new HashSet<>(requestHeaders.size());
        for (Map.Entry<String, String> requestHeader : requestHeaders) {
            httpRequest.addHeader(new BasicHeader(requestHeader.getKey(), requestHeader.getValue()));
            requestNames.add(requestHeader.getKey());
        }
        for (Header defaultHeader : defaultHeaders) {
            if (requestNames.contains(defaultHeader.getName()) == false) {
                httpRequest.addHeader(defaultHeader);
            }
        }
        if (compressionEnabled) {
            httpRequest.addHeader("Accept-Encoding", "gzip");
        }
    }
    /**
     * Called after each successful request call.
     * Receives as an argument the host that was used for the successful request.
     */
    private void onResponse(Node node) {
        DeadHostState removedHost = this.denylist.remove(node.getHost());
        if (logger.isDebugEnabled() && removedHost != null) {
            logger.debug("removed [" + node + "] from denylist");
        }
    }

    private <ResponseT> ResponseT decodeResponse(
            int statusCode, @Nullable HttpEntity entity, Response clientResp, Endpoint<?, ResponseT, ?> endpoint
        ) throws IOException {

        if (endpoint instanceof BooleanEndpoint) {
            BooleanEndpoint<?> bep = (BooleanEndpoint<?>) endpoint;

            @SuppressWarnings("unchecked")
            ResponseT response = (ResponseT) new BooleanResponse(bep.getResult(statusCode));
            return response;

        } else if (endpoint instanceof JsonEndpoint){
            JsonEndpoint<?, ResponseT, ?> jsonEndpoint = (JsonEndpoint<?, ResponseT, ?>)endpoint;
            // Successful response
            ResponseT response = null;
            JsonpDeserializer<ResponseT> responseParser = jsonEndpoint.responseDeserializer();
            if (responseParser != null) {
                // Expecting a body
                if (entity == null) {
                    throw new TransportException(
                        "Expecting a response body, but none was sent",
                        new ResponseException(clientResp)
                    );
                }
                InputStream content = entity.getContent();
                try (JsonParser parser = mapper.jsonProvider().createParser(content)) {
                    response = responseParser.deserialize(parser, mapper);
                };
            }
            return response;
        } else {
            throw new TransportException("Unhandled endpoint type: '" + endpoint.getClass().getName() + "'");
        }
    }

    /**
     * {@link NodeTuple} enables the {@linkplain Node}s and {@linkplain AuthCache}
     * to be set together in a thread safe, volatile way.
     */
    static class NodeTuple<T> {
        final T nodes;
        final AuthCache authCache;

        NodeTuple(final T nodes, final AuthCache authCache) {
            this.nodes = nodes;
            this.authCache = authCache;
        }
    }

    /**
     * Contains a reference to a denylisted node and the time until it is
     * revived. We use this so we can do a single pass over the denylist.
     */
    private static class DeadNode implements Comparable<DeadNode> {
        final Node node;
        final DeadHostState deadness;

        DeadNode(Node node, DeadHostState deadness) {
            this.node = node;
            this.deadness = deadness;
        }

        @Override
        public String toString() {
            return node.toString();
        }

        @Override
        public int compareTo(DeadNode rhs) {
            return deadness.compareTo(rhs.deadness);
        }
    }

    /**
     * Adapts an <code>Iterator&lt;DeadNodeAndRevival&gt;</code> into an
     * <code>Iterator&lt;Node&gt;</code>.
     */
    private static class DeadNodeIteratorAdapter implements Iterator<Node> {
        private final Iterator<DeadNode> itr;

        private DeadNodeIteratorAdapter(Iterator<DeadNode> itr) {
            this.itr = itr;
        }

        @Override
        public boolean hasNext() {
            return itr.hasNext();
        }

        @Override
        public Node next() {
            return itr.next().node;
        }

        @Override
        public void remove() {
            itr.remove();
        }
    }

    /**
     * Write an nd-json value by serializing each of its items on a separate line, recursing if its items themselves implement
     * {@link NdJsonpSerializable} to flattening nested structures.
     */
    private void writeNdJson(NdJsonpSerializable value, ByteArrayOutputStream baos) {
        Iterator<?> values = value._serializables();
        while(values.hasNext()) {
            Object item = values.next();
            if (item instanceof NdJsonpSerializable && item != value) { // do not recurse on the item itself
                writeNdJson((NdJsonpSerializable) item, baos);
            } else {
                JsonGenerator generator = mapper.jsonProvider().createGenerator(baos);
                mapper.serialize(item, generator);
                generator.close();
                baos.write('\n');
            }
        }
    }
    
    private static URI buildUri(String pathPrefix, String path, Map<String, String> params) {
        Objects.requireNonNull(path, "path must not be null");
        try {
            String fullPath;
            if (pathPrefix != null && pathPrefix.isEmpty() == false) {
                if (pathPrefix.endsWith("/") && path.startsWith("/")) {
                    fullPath = pathPrefix.substring(0, pathPrefix.length() - 1) + path;
                } else if (pathPrefix.endsWith("/") || path.startsWith("/")) {
                    fullPath = pathPrefix + path;
                } else {
                    fullPath = pathPrefix + "/" + path;
                }
            } else {
                fullPath = path;
            }

            URIBuilder uriBuilder = new URIBuilder(fullPath);
            for (Map.Entry<String, String> param : params.entrySet()) {
                uriBuilder.addParameter(param.getKey(), param.getValue());
            }

            // The Apache HttpClient 5.x **does not** encode URIs but Apache HttpClient 4.x does. It leads
            // to the issues with Unicode characters (f.e. document IDs could contain Unicode characters) and
            // weird characters are being passed instead. By using `toASCIIString()`, the URI is already created
            // with proper encoding.
            return new URI(uriBuilder.build().toASCIIString());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static class RequestContext {
        private final Node node;
        private final AsyncRequestProducer requestProducer;
        private final AsyncResponseConsumer<ClassicHttpResponse> asyncResponseConsumer;
        private final HttpClientContext context;

        RequestContext(final ApacheHttpClient5Options options, final HttpUriRequestBase request, 
                final Node node, final AuthCache authCache) {
            this.node = node;
            this.requestProducer = HttpUriRequestProducer.create(request, node.getHost());
            this.asyncResponseConsumer = options
                    .getHttpAsyncResponseConsumerFactory()
                    .createHttpAsyncResponseConsumer();
            this.context = HttpClientContext.create();
            context.setAuthCache(new WrappingAuthCache(context, authCache));
        }
    }
    
    /**
     * The Apache HttpClient 5 adds "Authorization" header even if the credentials for basic authentication are not provided
     * (effectively, username and password are 'null'). To workaround that, wrapping the AuthCache around current HttpClientContext
     * and ensuring that the credentials are indeed provided for particular HttpHost, otherwise returning no authentication scheme
     * even if it is present in the cache.
     */
    private static class WrappingAuthCache implements AuthCache {
        private final HttpClientContext context;
        private final AuthCache delegate;
        private final boolean usePersistentCredentials = true;

        WrappingAuthCache(HttpClientContext context, AuthCache delegate) {
            this.context = context;
            this.delegate = delegate;
        }

        @Override
        public void put(HttpHost host, AuthScheme authScheme) {
            delegate.put(host, authScheme);
        }

        @Override
        public AuthScheme get(HttpHost host) {
            AuthScheme authScheme = delegate.get(host);

            if (authScheme != null) {
                final CredentialsProvider credsProvider = context.getCredentialsProvider();
                if (credsProvider != null) {
                    final String schemeName = authScheme.getName();
                    final AuthScope authScope = new AuthScope(host, null, schemeName);
                    final Credentials creds = credsProvider.getCredentials(authScope, context);

                    // See please https://issues.apache.org/jira/browse/HTTPCLIENT-2203
                    if (authScheme instanceof BasicScheme) {
                        ((BasicScheme) authScheme).initPreemptive(creds);
                    }

                    if (creds == null) {
                        return null;
                    }
                }
            }

            return authScheme;
        }

        @Override
        public void remove(HttpHost host) {
            if (!usePersistentCredentials) {
                delegate.remove(host);
            }
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }

    private static class ResponseOrResponseException {
        private final Response response;
        private final ResponseException responseException;

        ResponseOrResponseException(Response response) {
            this.response = Objects.requireNonNull(response);
            this.responseException = null;
        }

        ResponseOrResponseException(ResponseException responseException) {
            this.responseException = Objects.requireNonNull(responseException);
            this.response = null;
        }
    }
    
    /**
     * Listener that allows to be notified whenever a failure happens. Useful when sniffing is enabled, so that we can sniff on failure.
     * The default implementation is a no-op.
     */
    public static class FailureListener {
        /**
         * Create a {@link FailureListener} instance.
         */
        public FailureListener() {}

        /**
         * Notifies that the node provided as argument has just failed.
         *
         * @param node The node which has failed.
         */
        public void onFailure(Node node) {}
    }
    
    /**
     * A gzip compressing entity that also implements {@code getContent()}.
     */
    public static class ContentCompressingEntity extends HttpEntityWrapper {
        private static final String GZIP_CODEC = "gzip";

        private Optional<Boolean> chunkedEnabled;

        /**
         * Creates a {@link ContentCompressingEntity} instance with the provided HTTP entity.
         *
         * @param entity the HTTP entity.
         */
        public ContentCompressingEntity(HttpEntity entity) {
            super(entity);
            this.chunkedEnabled = Optional.empty();
        }

        /**
         * Returns content encoding of the entity, if known.
         */
        @Override
        public String getContentEncoding() {
            return GZIP_CODEC;
        }

        /**
         * Creates a {@link ContentCompressingEntity} instance with the provided HTTP entity.
         *
         * @param entity the HTTP entity.
         * @param chunkedEnabled force enable/disable chunked transfer-encoding.
         */
        public ContentCompressingEntity(HttpEntity entity, boolean chunkedEnabled) {
            super(entity);
            this.chunkedEnabled = Optional.of(chunkedEnabled);
        }

        /**
         * Returns a content stream of the entity.
         */
        @Override
        public InputStream getContent() throws IOException {
            ByteArrayInputOutputStream out = new ByteArrayInputOutputStream(1024);
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(out)) {
                super.writeTo(gzipOut);
            }
            return out.asInput();
        }

        /**
         * A gzip compressing entity doesn't work with chunked encoding with sigv4
         *
         * @return false
         */
        @Override
        public boolean isChunked() {
            return chunkedEnabled.orElseGet(super::isChunked);
        }

        /**
         * A gzip entity requires content length in http headers.
         *
         * @return content length of gzip entity
         */
        @Override
        public long getContentLength() {
            if (chunkedEnabled.isPresent()) {
                if (chunkedEnabled.get()) {
                    return -1L;
                } else {
                    long size;
                    try (InputStream is = getContent()) {
                        size = is.readAllBytes().length;
                    } catch (IOException ex) {
                        size = -1L;
                    }

                    return size;
                }
            } else {
                return -1;
            }
        }

        /**
         * Writes the entity content out to the output stream.
         * @param outStream the output stream to write entity content to
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void writeTo(final OutputStream outStream) throws IOException {
            Args.notNull(outStream, "Output stream");
            final GZIPOutputStream gzip = new GZIPOutputStream(outStream);
            super.writeTo(gzip);
            // Only close output stream if the wrapped entity has been
            // successfully written out
            gzip.close();
        }
    }

    /**
     * An entity that lets the caller specify the return value of {@code isChunked()}.
     */
    public static class ContentHttpEntity extends HttpEntityWrapper {
        private Optional<Boolean> chunkedEnabled;

        /**
         * Creates a {@link ContentHttpEntity} instance with the provided HTTP entity.
         *
         * @param entity the HTTP entity.
         */
        public ContentHttpEntity(HttpEntity entity) {
            super(entity);
            this.chunkedEnabled = Optional.empty();
        }

        /**
         * Creates a {@link ContentHttpEntity} instance with the provided HTTP entity.
         *
         * @param entity the HTTP entity.
         * @param chunkedEnabled force enable/disable chunked transfer-encoding.
         */
        public ContentHttpEntity(HttpEntity entity, boolean chunkedEnabled) {
            super(entity);
            this.chunkedEnabled = Optional.of(chunkedEnabled);
        }

        /**
         * A chunked entity requires transfer-encoding:chunked in http headers
         * which requires isChunked to be true
         *
         * @return true
         */
        @Override
        public boolean isChunked() {
            return chunkedEnabled.orElseGet(super::isChunked);
        }
    }

    /**
     * A ByteArrayOutputStream that can be turned into an input stream without copying the underlying buffer.
     */
    private static class ByteArrayInputOutputStream extends ByteArrayOutputStream {
        ByteArrayInputOutputStream(int size) {
            super(size);
        }

        public InputStream asInput() {
            return new ByteArrayInputStream(this.buf, 0, this.count);
        }
    }
}
