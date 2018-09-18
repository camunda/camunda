/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RecordingStreamObserver<T> implements StreamObserver<T> {

  private int completionCount = 0;
  private final List<T> values = new ArrayList<>();
  private final List<Throwable> errors = new ArrayList<>();

  @Override
  public void onNext(T value) {
    values.add(value);
  }

  @Override
  public void onError(Throwable t) {
    errors.add(t);
  }

  @Override
  public void onCompleted() {
    completionCount++;
  }

  public int getCompletionCount() {
    return completionCount;
  }

  public List<T> getValues() {
    return values;
  }

  public List<Throwable> getErrors() {
    return errors;
  }

  public void assertValues(T... values) {
    assertThat(this.values).containsOnly(values);
    assertThat(completionCount).isEqualTo(1);
  }

  public void assertErrors(Throwable... errors) {
    final List<String> statusErrors =
        Arrays.stream(errors)
            .map(
                e ->
                    Status.INTERNAL
                        .augmentDescription(e.getMessage())
                        .asRuntimeException()
                        .getMessage())
            .collect(Collectors.toList());

    assertThat(this.errors).extracting(Throwable::getMessage).containsOnlyElementsOf(statusErrors);
  }
}
