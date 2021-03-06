package org.zalando.riptide;

/*
 * ⁣​
 * Riptide
 * ⁣⁣
 * Copyright (C) 2015 Zalando SE
 * ⁣⁣
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ​⁣
 */

import com.google.common.reflect.TypeToken;
import org.springframework.http.client.ClientHttpResponse;

import java.util.Optional;

import static java.util.Arrays.asList;
import static org.zalando.riptide.Capture.none;

public final class UntypedCondition<A> {

    private final Router router = new Router();
    private final Optional<A> attribute;

    UntypedCondition(final Optional<A> attribute) {
        this.attribute = attribute;
    }

    public Binding<A> call(final ThrowingConsumer<ClientHttpResponse, ?> consumer) {
        return Binding.create(attribute, (response, converters) -> {
            consumer.accept(response);
            return none();
        });
    }

    public Binding<A> call(final ThrowingRunnable<?> consumer) {
        return Binding.create(attribute, (response, converters) -> {
            consumer.run();
            return none();
        });
    }

    public Capturer<A> map(final ThrowingFunction<ClientHttpResponse, ?, ?> function) {
        return () -> Binding.create(attribute, (response, converters) ->
                Capture.valueOf(function.apply(response)));
    }

    public <T> Capturer<A> map(final ThrowingFunction<ClientHttpResponse, T, ?> function, final Class<T> mappedType) {
        return map(function, TypeToken.of(mappedType));
    }

    public <T> Capturer<A> map(final ThrowingFunction<ClientHttpResponse, T, ?> function, final TypeToken<T> mappedType) {
        return () -> Binding.create(attribute, (response, converters) ->
                Capture.valueOf(function.apply(response), mappedType));
    }

    public Binding<A> capture() {
        return Binding.create(attribute, (response, converters) ->
                Capture.valueOf(response, ClientHttpResponse.class));
    }

    public Binding<A> capture(final ThrowingFunction<ClientHttpResponse, ?, ?> function) {
        return map(function).capture();
    }

    public <T> Binding<A> capture(final ThrowingFunction<ClientHttpResponse, T, ?> function, final Class<T> mappedType) {
        return capture(function, TypeToken.of(mappedType));
    }

    public <T> Binding<A> capture(final ThrowingFunction<ClientHttpResponse, T, ?> function, final TypeToken<T> mappedType) {
        return map(function, mappedType).capture();
    }

    @SafeVarargs
    public final <B> Binding<A> dispatch(final Selector<B> selector, final Binding<B>... bindings) {
        return Binding.create(attribute, (response, converters) ->
                router.route(response, converters, selector, asList(bindings)));
    }

}
