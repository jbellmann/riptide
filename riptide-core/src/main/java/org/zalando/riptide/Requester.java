package org.zalando.riptide;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.AsyncClientHttpRequest;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.util.concurrent.FailureCallback;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SuccessCallback;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.google.common.collect.ObjectArrays.concat;

public final class Requester extends Dispatcher {

    private final AsyncClientHttpRequestFactory requestFactory;
    private final MessageWorker worker;
    private final HttpMethod method;
    private final UriComponentsBuilder requestUri;

    private final Multimap<String, String> query = LinkedHashMultimap.create();
    private final HttpHeaders headers = new HttpHeaders();

    public Requester(final AsyncClientHttpRequestFactory requestFactory, final MessageWorker worker,
            final HttpMethod method, final UriComponentsBuilder requestUri) {
        this.requestFactory = requestFactory;
        this.worker = worker;
        this.method = method;
        this.requestUri = requestUri;
    }

    public final Requester queryParam(final String name, final String value) {
        query.put(name, value);
        return this;
    }

    public final Requester queryParams(final Multimap<String, String> params) {
        query.putAll(params);
        return this;
    }

    public final Requester header(final String name, final String value) {
        headers.add(name, value);
        return this;
    }

    public final Requester headers(final HttpHeaders headers) {
        this.headers.putAll(headers);
        return this;
    }

    public final Requester accept(final MediaType acceptableMediaType, final MediaType... acceptableMediaTypes) {
        headers.setAccept(Lists.asList(acceptableMediaType, acceptableMediaTypes));
        return this;
    }

    public final Requester contentType(final MediaType contentType) {
        headers.setContentType(contentType);
        return this;
    }

    public final <T> Dispatcher body(@Nullable final T body) {
        return execute(query, headers, body);
    }

    @Override
    public CompletableFuture<Void> call(final Route route) {
        return execute(query, headers, null).call(route);
    }

    private <T> Dispatcher execute(final Multimap<String, String> query, final HttpHeaders headers,
            final @Nullable T body) {

        final HttpEntity<T> entity = new HttpEntity<>(body, headers);
        final ListenableFuture<ClientHttpResponse> listenable = createAndExecute(query, entity);

        /*
         * A good way to store a stacktrace away efficiently is to simply construct an exception. Later, if you want to
         * inspect the stacktrace call exception.getStackTrace() which will do the slow work of resolving the stack
         * frames to methods.
         *
         * <a href="http://stackoverflow.com/a/4377609/232539>What is the proper way to keep track of the original stack trace in a newly created Thread?</a>
         */
        final Supplier<StackTraceElement[]> originalStackTrace = new Exception()::getStackTrace;

        return new ResponseDispatcher(worker, listenable, originalStackTrace);
    }

    private <T> ListenableFuture<ClientHttpResponse> createAndExecute(final Multimap<String, String> query,
            final HttpEntity<T> entity) {
        try {
            final AsyncClientHttpRequest request = createRequest(query, entity);
            return request.executeAsync();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> AsyncClientHttpRequest createRequest(final Multimap<String, String> query,
            final HttpEntity<T> entity) throws IOException {

        final URI requestUri = prepareRequestUri(query);
        final AsyncClientHttpRequest request = requestFactory.createAsyncRequest(requestUri, method);
        worker.write(request, entity);
        return request;
    }

    private URI prepareRequestUri(final Multimap<String, String> query) {
        // we have to encode query params separately, because the rest of the URI is already encoded
        requestUri.queryParams(encodeQueryParams(query));
        return requestUri.build(true).normalize().toUri();
    }

    private MultiValueMap<String, String> encodeQueryParams(final Multimap<String, String> query) {
        final UriComponentsBuilder components = UriComponentsBuilder.fromUriString("");

        query.entries().forEach(entry ->
                components.queryParam(entry.getKey(), entry.getValue()));

        return components.build().encode().getQueryParams();
    }

    private static class ResponseDispatcher extends Dispatcher {

        private final MessageWorker worker;
        private final ListenableFuture<ClientHttpResponse> listenable;
        private final Supplier<StackTraceElement[]> originalStackTrace;

        public ResponseDispatcher(final MessageWorker worker, final ListenableFuture<ClientHttpResponse> listenable,
                final Supplier<StackTraceElement[]> originalStackTrace) {
            this.worker = worker;
            this.listenable = listenable;
            this.originalStackTrace = originalStackTrace;
        }

        @Override
        public CompletableFuture<Void> call(final Route route) {
            final CompletableFuture<Void> future = new ListenableCompletableFutureAdapter(listenable);

            final SuccessCallback<ClientHttpResponse> onSuccess = response -> dispatch(response, route, future);
            final FailureCallback onFailure = cause -> complete(future, cause);
            listenable.addCallback(onSuccess, onFailure);

            return future;
        }

        private void dispatch(final ClientHttpResponse response, final Route route, final CompletableFuture<Void> future) {
            try {
                try {
                    route.execute(response, worker);
                    future.complete(null);
                } catch (final NoWildcardException e) {
                    throw new NoRouteException(response);
                }
            } catch (final Exception cause) {
                complete(future, cause);
            }
        }

        private void complete(final CompletableFuture<Void> future, final Throwable cause) {
            future.completeExceptionally(appendOriginalStackTrace(cause));
        }

        private Throwable appendOriginalStackTrace(final Throwable cause) {
            cause.setStackTrace(concat(cause.getStackTrace(), originalStackTrace.get(), StackTraceElement.class));
            return cause;
        }

    }

}